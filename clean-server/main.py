from __future__ import annotations

import hashlib
import os
import secrets
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, EmailStr, Field

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = Path(os.getenv("TSCORE_DB_PATH", BASE_DIR / "tscore-clean.db"))

app = FastAPI(title="T-Score Clean API", version="0.1.0")


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db() -> None:
    with db() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                email TEXT NOT NULL UNIQUE COLLATE NOCASE,
                full_name TEXT NOT NULL,
                password_salt TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                trust_score INTEGER NOT NULL DEFAULT 50,
                created_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS sessions (
                token TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            CREATE TABLE IF NOT EXISTS deals (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                amount REAL NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'OPEN',
                created_at TEXT NOT NULL,
                FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE
            );
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_id INTEGER NOT NULL,
                recipient_id INTEGER NOT NULL,
                body TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(sender_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY(recipient_id) REFERENCES users(id) ON DELETE CASCADE
            );
            """
        )


@app.on_event("startup")
def startup() -> None:
    init_db()


def hash_password(password: str, salt_hex: Optional[str] = None) -> tuple[str, str]:
    salt = bytes.fromhex(salt_hex) if salt_hex else secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, 200_000)
    return salt.hex(), digest.hex()


class SignupRequest(BaseModel):
    username: str = Field(min_length=3, max_length=32, pattern=r"^[A-Za-z0-9_.-]+$")
    email: EmailStr
    full_name: str = Field(min_length=2, max_length=80)
    password: str = Field(min_length=8, max_length=128)


class LoginRequest(BaseModel):
    login: str = Field(min_length=3, max_length=120)
    password: str = Field(min_length=8, max_length=128)


class DealCreate(BaseModel):
    title: str = Field(min_length=2, max_length=120)
    amount: float = Field(ge=0)


class MessageCreate(BaseModel):
    recipient_id: int = Field(gt=0)
    body: str = Field(min_length=1, max_length=4000)


def user_payload(row: sqlite3.Row) -> dict:
    return {
        "id": row["id"],
        "username": row["username"],
        "email": row["email"],
        "full_name": row["full_name"],
        "trust_score": row["trust_score"],
        "created_at": row["created_at"],
    }


def issue_token(conn: sqlite3.Connection, user_id: int) -> str:
    token = secrets.token_urlsafe(32)
    conn.execute(
        "INSERT INTO sessions(token,user_id,created_at) VALUES(?,?,?)",
        (token, user_id, now_iso()),
    )
    return token


def current_user(authorization: Optional[str] = Header(default=None)) -> sqlite3.Row:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Authentication required")
    token = authorization[7:].strip()
    with db() as conn:
        row = conn.execute(
            "SELECT u.* FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token=?",
            (token,),
        ).fetchone()
    if not row:
        raise HTTPException(401, "Invalid session")
    return row


@app.get("/health")
def health() -> dict:
    return {"ok": True, "service": "t-score-clean", "version": "0.1.0"}


@app.post("/api/v1/auth/signup")
def signup(body: SignupRequest) -> dict:
    salt, password_hash = hash_password(body.password)
    try:
        with db() as conn:
            cur = conn.execute(
                "INSERT INTO users(username,email,full_name,password_salt,password_hash,created_at) VALUES(?,?,?,?,?,?)",
                (body.username.strip(), str(body.email).lower(), body.full_name.strip(), salt, password_hash, now_iso()),
            )
            token = issue_token(conn, cur.lastrowid)
            row = conn.execute("SELECT * FROM users WHERE id=?", (cur.lastrowid,)).fetchone()
            return {"token": token, "user": user_payload(row)}
    except sqlite3.IntegrityError:
        raise HTTPException(409, "Username or email already exists")


@app.post("/api/v1/auth/login")
def login(body: LoginRequest) -> dict:
    with db() as conn:
        row = conn.execute(
            "SELECT * FROM users WHERE username=? COLLATE NOCASE OR email=? COLLATE NOCASE",
            (body.login.strip(), body.login.strip()),
        ).fetchone()
        if not row:
            raise HTTPException(401, "Invalid login")
        _, candidate = hash_password(body.password, row["password_salt"])
        if not secrets.compare_digest(candidate, row["password_hash"]):
            raise HTTPException(401, "Invalid login")
        token = issue_token(conn, row["id"])
        return {"token": token, "user": user_payload(row)}


@app.get("/api/v1/me")
def me(user: sqlite3.Row = Depends(current_user)) -> dict:
    return user_payload(user)


@app.get("/api/v1/people")
def people(user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        rows = conn.execute(
            "SELECT * FROM users WHERE id<>? ORDER BY trust_score DESC, id DESC LIMIT 100",
            (user["id"],),
        ).fetchall()
    return {"items": [user_payload(r) for r in rows]}


@app.get("/api/v1/deals")
def list_deals(user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        rows = conn.execute(
            "SELECT id,title,amount,status,created_at FROM deals WHERE owner_id=? ORDER BY id DESC",
            (user["id"],),
        ).fetchall()
    return {"items": [dict(r) for r in rows]}


@app.post("/api/v1/deals")
def create_deal(body: DealCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        cur = conn.execute(
            "INSERT INTO deals(owner_id,title,amount,status,created_at) VALUES(?,?,?,?,?)",
            (user["id"], body.title.strip(), body.amount, "OPEN", now_iso()),
        )
        row = conn.execute(
            "SELECT id,title,amount,status,created_at FROM deals WHERE id=?",
            (cur.lastrowid,),
        ).fetchone()
    return dict(row)


@app.get("/api/v1/chats")
def chats(user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        rows = conn.execute(
            """
            SELECT m.id,m.sender_id,m.recipient_id,m.body,m.created_at
            FROM messages m
            WHERE m.sender_id=? OR m.recipient_id=?
            ORDER BY m.id DESC LIMIT 100
            """,
            (user["id"], user["id"]),
        ).fetchall()
    return {"items": [dict(r) for r in rows]}


@app.post("/api/v1/messages")
def send_message(body: MessageCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    if body.recipient_id == user["id"]:
        raise HTTPException(400, "Cannot message yourself")
    with db() as conn:
        recipient = conn.execute("SELECT id FROM users WHERE id=?", (body.recipient_id,)).fetchone()
        if not recipient:
            raise HTTPException(404, "Recipient not found")
        cur = conn.execute(
            "INSERT INTO messages(sender_id,recipient_id,body,created_at) VALUES(?,?,?,?)",
            (user["id"], body.recipient_id, body.body.strip(), now_iso()),
        )
        row = conn.execute("SELECT * FROM messages WHERE id=?", (cur.lastrowid,)).fetchone()
    return dict(row)
