-- Phase 0 — Foundations schema.
-- Tables: project, component, app_user, role, role_assignment, item, item_revision, audit_entry.
-- UUID primary keys are assigned by the application (DB-agnostic). Enums are stored as
-- varchar with CHECK constraints (constrained vocabulary, PLAN §12). Audited tables are
-- append-only / never hard-deleted.

-- ---------------------------------------------------------------------------
-- Identity & access
-- ---------------------------------------------------------------------------
CREATE TABLE app_user (
    id          UUID         PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sso_subject VARCHAR(255),
    created_at  TIMESTAMPTZ    NOT NULL
);

CREATE TABLE role (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE
        CHECK (name IN ('AUTHOR', 'REVIEWER', 'APPROVER', 'QA', 'ADMIN'))
);

CREATE TABLE role_assignment (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES app_user (id),
    role_id     UUID         NOT NULL REFERENCES role (id),
    project_id  UUID,  -- NULL = global assignment
    CONSTRAINT uq_role_assignment UNIQUE (user_id, role_id, project_id)
);

-- ---------------------------------------------------------------------------
-- Project & component
-- ---------------------------------------------------------------------------
CREATE TABLE project (
    id                   UUID         PRIMARY KEY,
    key                  VARCHAR(10)  NOT NULL UNIQUE,
    name                 VARCHAR(255) NOT NULL,
    description          TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    default_safety_class VARCHAR(1)
        CHECK (default_safety_class IN ('A', 'B', 'C')),
    created_at           TIMESTAMPTZ    NOT NULL,
    created_by           UUID         REFERENCES app_user (id)
);

CREATE TABLE component (
    id                  UUID         PRIMARY KEY,
    project_id          UUID         NOT NULL REFERENCES project (id),
    key                 VARCHAR(12)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    parent_component_id UUID         REFERENCES component (id),
    safety_class        VARCHAR(1)
        CHECK (safety_class IN ('A', 'B', 'C')),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at          TIMESTAMPTZ    NOT NULL,
    created_by          UUID         REFERENCES app_user (id),
    CONSTRAINT uq_component_key UNIQUE (project_id, key)
);

-- ---------------------------------------------------------------------------
-- Item (stable identity) + ItemRevision (versioned, audited content)
-- ---------------------------------------------------------------------------
CREATE TABLE item (
    id                  UUID         PRIMARY KEY,
    project_id          UUID         NOT NULL REFERENCES project (id),
    component_id        UUID         REFERENCES component (id),
    type                VARCHAR(30)  NOT NULL
        CHECK (type IN ('USER_NEED', 'SYSTEM_REQUIREMENT', 'SOFTWARE_REQUIREMENT',
                        'DESIGN_SPEC', 'HAZARD', 'RISK', 'RISK_CONTROL',
                        'SFMEA_ENTRY', 'TEST_CASE', 'COMPOSITE_TEST')),
    human_key           VARCHAR(60)  NOT NULL UNIQUE,
    current_revision_id UUID,  -- FK added after item_revision exists
    created_at          TIMESTAMPTZ    NOT NULL,
    created_by          UUID         REFERENCES app_user (id)
);

CREATE TABLE item_revision (
    id                 UUID         PRIMARY KEY,
    item_id            UUID         NOT NULL REFERENCES item (id),
    revision_no        INTEGER      NOT NULL,
    parent_revision_id UUID         REFERENCES item_revision (id),
    is_current         BOOLEAN      NOT NULL DEFAULT TRUE,
    lifecycle_status   VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CHECK (lifecycle_status IN ('DRAFT', 'IN_REVIEW', 'APPROVED',
                                    'DECLINED', 'DEPRECATED', 'RETIRED')),
    title              VARCHAR(500) NOT NULL,
    statement          TEXT         NOT NULL,
    rationale          TEXT,
    author_id          UUID         NOT NULL REFERENCES app_user (id),
    created_at         TIMESTAMPTZ    NOT NULL,
    change_reason      TEXT,
    frozen             BOOLEAN      NOT NULL DEFAULT FALSE,
    frozen_at          TIMESTAMPTZ,
    content_hash       VARCHAR(64)  NOT NULL,
    CONSTRAINT uq_item_revision_no UNIQUE (item_id, revision_no)
);

ALTER TABLE item
    ADD CONSTRAINT fk_item_current_revision
        FOREIGN KEY (current_revision_id) REFERENCES item_revision (id);

CREATE INDEX idx_item_revision_item ON item_revision (item_id);
CREATE INDEX idx_item_project ON item (project_id);

-- ---------------------------------------------------------------------------
-- Audit trail (Part 11, append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_entry (
    id            UUID         PRIMARY KEY,
    entity_type   VARCHAR(100) NOT NULL,
    entity_id     UUID         NOT NULL,
    action        VARCHAR(20)  NOT NULL
        CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'STATUS_CHANGE', 'SIGN', 'RELEASE')),
    actor_user_id UUID         REFERENCES app_user (id),
    occurred_at   TIMESTAMPTZ    NOT NULL,
    field         VARCHAR(100),
    old_value     TEXT,
    new_value     TEXT,
    reason        TEXT,
    session_ref   VARCHAR(255)
);

CREATE INDEX idx_audit_entity ON audit_entry (entity_type, entity_id);
CREATE INDEX idx_audit_occurred ON audit_entry (occurred_at);
