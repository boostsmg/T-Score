from __future__ import annotations

import hashlib
import os
import secrets
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Query
from pydantic import BaseModel, EmailStr, Field

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = Path(os.getenv("TSCORE_DB_PATH", BASE_DIR / "tscore-clean.db"))

app = FastAPI(title="T-Score Clean API", version="0.4.0")


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
                counterparty_id INTEGER,
                title TEXT NOT NULL,
                amount REAL NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'PENDING',
                created_at TEXT NOT NULL,
                updated_at TEXT,
                FOREIGN KEY(owner_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY(counterparty_id) REFERENCES users(id) ON DELETE SET NULL
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
        columns = {r[1] for r in conn.execute("PRAGMA table_info(deals)").fetchall()}
        if "counterparty_id" not in columns:
            conn.execute("ALTER TABLE deals ADD COLUMN counterparty_id INTEGER")
        if "updated_at" not in columns:
            conn.execute("ALTER TABLE deals ADD COLUMN updated_at TEXT")


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
    counterparty_id: Optional[int] = Field(default=None, gt=0)


class DealStatusUpdate(BaseModel):
    status: str


class MessageCreate(BaseModel):
    recipient_id: int = Field(gt=0)
    body: str = Field(min_length=1, max_length=4000)


def user_payload(row: sqlite3.Row, include_email: bool = True) -> dict:
    payload = {
        "id": row["id"],
        "username": row["username"],
        "full_name": row["full_name"],
        "trust_score": row["trust_score"],
        "created_at": row["created_at"],
    }
    if include_email:
        payload["email"] = row["email"]
    return payload


def public_user_payload(row: sqlite3.Row) -> dict:
    return user_payload(row, include_email=False)


def issue_token(conn: sqlite3.Connection, user_id: int) -> str:
    token = secrets.token_urlsafe(32)
    conn.execute("INSERT INTO sessions(token,user_id,created_at) VALUES(?,?,?)", (token, user_id, now_iso()))
    return token


def bearer_token(authorization: Optional[str]) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Authentication required")
    token = authorization[7:].strip()
    if not token:
        raise HTTPException(401, "Authentication required")
    return token


def current_user(authorization: Optional[str] = Header(default=None)) -> sqlite3.Row:
    token = bearer_token(authorization)
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
    return {"ok": True, "service": "t-score-clean", "version": "0.4.0"}


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


@app.post("/api/v1/auth/logout")
def logout(authorization: Optional[str] = Header(default=None)) -> dict:
    token = bearer_token(authorization)
    with db() as conn:
        conn.execute("DELETE FROM sessions WHERE token=?", (token,))
    return {"ok": True}


@app.get("/api/v1/me")
def me(user: sqlite3.Row = Depends(current_user)) -> dict:
    return user_payload(user)


@app.get("/api/v1/people")
def people(
    q: str = Query(default="", max_length=80),
    user: sqlite3.Row = Depends(current_user),
) -> dict:
    term = q.strip()
    with db() as conn:
        if term:
            like = f"%{term}%"
            rows = conn.execute(
                """
                SELECT * FROM users
                WHERE id<>? AND (username LIKE ? COLLATE NOCASE OR full_name LIKE ? COLLATE NOCASE)
                ORDER BY trust_score DESC, id DESC LIMIT 100
                """,
                (user["id"], like, like),
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM users WHERE id<>? ORDER BY trust_score DESC, id DESC LIMIT 100",
                (user["id"],),
            ).fetchall()
    return {"items": [public_user_payload(r) for r in rows]}


@app.get("/api/v1/people/{person_id}")
def person_profile(person_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        row = conn.execute("SELECT * FROM users WHERE id=?", (person_id,)).fetchone()
    if not row:
        raise HTTPException(404, "User not found")
    return public_user_payload(row)


def deal_payload(conn: sqlite3.Connection, row: sqlite3.Row) -> dict:
    result = dict(row)
    if row["counterparty_id"]:
        person = conn.execute("SELECT * FROM users WHERE id=?", (row["counterparty_id"],)).fetchone()
        result["counterparty"] = public_user_payload(person) if person else None
    else:
        result["counterparty"] = None
    return result


@app.get("/api/v1/deals")
def list_deals(user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        rows = conn.execute(
            """
            SELECT id,owner_id,counterparty_id,title,amount,status,created_at,updated_at
            FROM deals WHERE owner_id=? OR counterparty_id=? ORDER BY id DESC
            """,
            (user["id"], user["id"]),
        ).fetchall()
        items = [deal_payload(conn, r) for r in rows]
    return {"items": items}


@app.post("/api/v1/deals")
def create_deal(body: DealCreate, user: sqlite3.Row = Depends(current_user)) -> dict:
    if body.counterparty_id == user["id"]:
        raise HTTPException(400, "Cannot create a deal with yourself")
    with db() as conn:
        if body.counterparty_id:
            person = conn.execute("SELECT id FROM users WHERE id=?", (body.counterparty_id,)).fetchone()
            if not person:
                raise HTTPException(404, "Counterparty not found")
        cur = conn.execute(
            "INSERT INTO deals(owner_id,counterparty_id,title,amount,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",
            (user["id"], body.counterparty_id, body.title.strip(), body.amount, "PENDING", now_iso(), now_iso()),
        )
        row = conn.execute(
            "SELECT id,owner_id,counterparty_id,title,amount,status,created_at,updated_at FROM deals WHERE id=?",
            (cur.lastrowid,),
        ).fetchone()
        return deal_payload(conn, row)


@app.patch("/api/v1/deals/{deal_id}/status")
def update_deal_status(deal_id: int, body: DealStatusUpdate, user: sqlite3.Row = Depends(current_user)) -> dict:
    allowed = {"PENDING", "ACCEPTED", "IN_PROGRESS", "COMPLETED", "CANCELLED", "DISPUTED"}
    status = body.status.strip().upper()
    if status not in allowed:
        raise HTTPException(400, "Invalid deal status")
    with db() as conn:
        row = conn.execute("SELECT * FROM deals WHERE id=?", (deal_id,)).fetchone()
        if not row:
            raise HTTPException(404, "Deal not found")
        if user["id"] not in {row["owner_id"], row["counterparty_id"]}:
            raise HTTPException(403, "Not allowed")
        conn.execute("UPDATE deals SET status=?,updated_at=? WHERE id=?", (status, now_iso(), deal_id))
        updated = conn.execute(
            "SELECT id,owner_id,counterparty_id,title,amount,status,created_at,updated_at FROM deals WHERE id=?",
            (deal_id,),
        ).fetchone()
        return deal_payload(conn, updated)


@app.get("/api/v1/chats")
def chats(user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        rows = conn.execute(
            """
            SELECT m.* FROM messages m
            JOIN (
                SELECT CASE WHEN sender_id=? THEN recipient_id ELSE sender_id END AS partner_id, MAX(id) AS max_id
                FROM messages WHERE sender_id=? OR recipient_id=? GROUP BY partner_id
            ) latest ON latest.max_id=m.id
            ORDER BY m.id DESC
            """,
            (user["id"], user["id"], user["id"]),
        ).fetchall()
        items = []
        for row in rows:
            partner_id = row["recipient_id"] if row["sender_id"] == user["id"] else row["sender_id"]
            partner = conn.execute("SELECT * FROM users WHERE id=?", (partner_id,)).fetchone()
            items.append({
                "partner": public_user_payload(partner) if partner else {"id": partner_id},
                "last_message": row["body"],
                "last_message_at": row["created_at"],
            })
    return {"items": items}


@app.get("/api/v1/chats/{person_id}/messages")
def conversation(person_id: int, user: sqlite3.Row = Depends(current_user)) -> dict:
    with db() as conn:
        partner = conn.execute("SELECT * FROM users WHERE id=?", (person_id,)).fetchone()
        if not partner:
            raise HTTPException(404, "User not found")
        rows = conn.execute(
            """
            SELECT id,sender_id,recipient_id,body,created_at FROM messages
            WHERE (sender_id=? AND recipient_id=?) OR (sender_id=? AND recipient_id=?)
            ORDER BY id ASC LIMIT 300
            """,
            (user["id"], person_id, person_id, user["id"]),
        ).fetchall()
    return {"partner": public_user_payload(partner), "items": [dict(r) for r in rows]}


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
