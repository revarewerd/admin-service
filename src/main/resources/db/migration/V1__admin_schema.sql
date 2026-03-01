-- ============================================================
-- Admin Service: Миграция V1 — создание схемы admin
-- Таблицы: background_tasks, admin_audit_log
-- ============================================================

CREATE SCHEMA IF NOT EXISTS admin;

-- ============================================================
-- Фоновые задачи
-- ============================================================
CREATE TABLE admin.background_tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_type       VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'Pending',
    progress        INTEGER NOT NULL DEFAULT 0,
    parameters      JSONB,
    result          TEXT,
    error           TEXT,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bg_tasks_status ON admin.background_tasks(status);
CREATE INDEX idx_bg_tasks_type ON admin.background_tasks(task_type);
CREATE INDEX idx_bg_tasks_created ON admin.background_tasks(created_at DESC);

-- ============================================================
-- Аудит действий администратора
-- ============================================================
CREATE TABLE admin.admin_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID,
    user_id         UUID NOT NULL,
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       UUID,
    details         JSONB,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_audit_date ON admin.admin_audit_log(created_at DESC);
CREATE INDEX idx_admin_audit_user ON admin.admin_audit_log(user_id);
CREATE INDEX idx_admin_audit_company ON admin.admin_audit_log(company_id);
