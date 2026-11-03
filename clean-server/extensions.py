from __future__ import annotations

import os
import sqlite3
from pathlib import Path
from typing import Optional

from fastapi import Header, HTTPException
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = Path(os.getenv("TSCORE_DB_PATH", BASE_DIR / "tscore-clean.db"))


def db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def ensure_tables() -> None:
    with db() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS reviews (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                deal_id INTEGER NOT NULL,
                reviewer_id INTEGER NOT NULL,
                reviewed_user_id INTEGER NOT NULL,
                rating INTEGER NOT NULL CHECK(rating BETWEEN 1 AND 5),
                comment TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(deal_id, reviewer_id),
                FOREIGN KEY(deal_id) REFERENCES deals(id) ON DELETE CASCADE,
                FOREIGN KEY(reviewer_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY(reviewed_user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            CREATE TABLE IF NOT EXISTS blocks (
                blocker_id INTEGER NOT NULL,
                blocked_id INTEGER NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY(blocker_id, blocked_id),
                FOREIGN KEY(blocker_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY(blocked_id) REFERENCES users(id) ON DELETE CASCADE
            );
            CREATE TABLE IF NOT EXISTS reports (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                reporter_id INTEGER NOT NULL,
                reported_user_id INTEGER NOT NULL,
                reason TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(reporter_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY(reported_user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            """
        )


def current_user(authorization: Optional[str]) -> sqlite3.Row:
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


class ProfileUpdate(BaseModel):
    full_name: str = Field(min_length=2, max_length=80)


class ReviewCreate(BaseModel):
    rating: int = Field(ge=1, le=5)
    comment: str = Field(default="", max_length=500)


class ReportCreate(BaseModel):
    reason: str = Field(min_length=3, max_length=500)


def recalculate_trust(user_id: int) -> int:
    with db() as conn:
        row = conn.execute(
            "SELECT COUNT(*) AS c, AVG(rating) AS avg_rating FROM reviews WHERE reviewed_user_id=?",
            (user_id,),
        ).fetchone()
        count = int(row["c"] or 0)
        avg = float(row["avg_rating"] or 0)
        if count == 0:
            score = 50
        else:
            score = round(50 + (avg - 3.0) * 15 + min(count, 10) * 2)
            score = max(0, min(100, score))
        conn.execute("UPDATE users SET trust_score=? WHERE id=?", (score, user_id))
    return score


def register_routes(app) -> None:
    ensure_tables()

    @app.patch("/api/v1/me")
    def update_me(body: ProfileUpdate, authorization: Optional[str] = Header(default=None)) -> dict:
        user = current_user(authorization)
        with db() as conn:
            conn.execute("UPDATE users SET full_name=? WHERE id=?", (body.full_name.strip(), user["id"]))
            row = conn.execute("SELECT * FROM users WHERE id=?", (user["id"],)).fetchone()
        return {
            "id": row["id"], "username": row["username"], "email": row["email"],
            "full_name": row["full_name"], "trust_score": row["trust_score"], "created_at": row["created_at"]
        }

    @app.post("/api/v1/deals/{deal_id}/review")
    def create_review(deal_id: int, body: ReviewCreate, authorization: Optional[str] = Header(default=None)) -> dict:
        user = current_user(authorization)
        with db() as conn:
            deal = conn.execute("SELECT * FROM deals WHERE id=?", (deal_id,)).fetchone()
            if not deal:
                raise HTTPException(404, "Deal not found")
            if deal["status"] != "COMPLETED":
                raise HTTPException(400, "Deal must be completed before review")
            if user["id"] not in {deal["owner_id"], deal["counterparty_id"]}:
                raise HTTPException(403, "Not allowed")
            reviewed_id = deal["counterparty_id"] if user["id"] == deal["owner_id"] else deal["owner_id"]
            if not reviewed_id:
                raise HTTPException(400, "This deal has no counterparty")
            try:
                cur = conn.execute(
                    "INSERT INTO reviews(deal_id,reviewer_id,reviewed_user_id,rating,comment) VALUES(?,?,?,?,?)",
                    (deal_id, user["id"], reviewed_id, body.rating, body.comment.strip()),
                )
            except sqlite3.IntegrityError:
                raise HTTPException(409, "You already reviewed this deal")
        score = recalculate_trust(reviewed_id)
        return {"id": cur.lastrowid, "reviewed_user_id": reviewed_id, "rating": body.rating, "trust_score": score}

    @app.get("/api/v1/people/{person_id}/reviews")
    def list_reviews(person_id: int, authorization: Optional[str] = Header(default=None)) -> dict:
        current_user(authorization)
        with db() as conn:
            rows = conn.execute(
                """
                SELECT r.id,r.deal_id,r.rating,r.comment,r.created_at,u.username AS reviewer_username,u.full_name AS reviewer_name
                FROM reviews r JOIN users u ON u.id=r.reviewer_id
                WHERE r.reviewed_user_id=? ORDER BY r.id DESC LIMIT 100
                """,
                (person_id,),
            ).fetchall()
        return {"items": [dict(r) for r in rows]}

    @app.post("/api/v1/people/{person_id}/block")
    def block_user(person_id: int, authorization: Optional[str] = Header(default=None)) -> dict:
        user = current_user(authorization)
        if person_id == user["id"]:
            raise HTTPException(400, "Cannot block yourself")
        with db() as conn:
            exists = conn.execute("SELECT id FROM users WHERE id=?", (person_id,)).fetchone()
            if not exists:
                raise HTTPException(404, "User not found")
            conn.execute("INSERT OR IGNORE INTO blocks(blocker_id,blocked_id) VALUES(?,?)", (user["id"], person_id))
        return {"ok": True}

    @app.delete("/api/v1/people/{person_id}/block")
    def unblock_user(person_id: int, authorization: Optional[str] = Header(default=None)) -> dict:
        user = current_user(authorization)
        with db() as conn:
            conn.execute("DELETE FROM blocks WHERE blocker_id=? AND blocked_id=?", (user["id"], person_id))
        return {"ok": True}

    @app.post("/api/v1/people/{person_id}/report")
    def report_user(person_id: int, body: ReportCreate, authorization: Optional[str] = Header(default=None)) -> dict:
        user = current_user(authorization)
        if person_id == user["id"]:
            raise HTTPException(400, "Cannot report yourself")
        with db() as conn:
            exists = conn.execute("SELECT id FROM users WHERE id=?", (person_id,)).fetchone()
            if not exists:
                raise HTTPException(404, "User not found")
            cur = conn.execute("INSERT INTO reports(reporter_id,reported_user_id,reason) VALUES(?,?,?)", (user["id"], person_id, body.reason.strip()))
        return {"id": cur.lastrowid, "ok": True}
