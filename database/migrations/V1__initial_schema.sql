-- V1: Initial schema
CREATE TABLE IF NOT EXISTS plans (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    target_date TEXT,
    user_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS goals (
    id TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    target_value INTEGER NOT NULL DEFAULT 0,
    current_value INTEGER NOT NULL DEFAULT 0,
    unit TEXT NOT NULL DEFAULT 'COUNT',
    plan_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS steps (
    id TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    estimated_duration_seconds INTEGER,
    order_index INTEGER NOT NULL DEFAULT 0,
    plan_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE CASCADE
);
