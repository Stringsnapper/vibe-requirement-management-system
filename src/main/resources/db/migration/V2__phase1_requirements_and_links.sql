-- Phase 1 — Requirements core.
-- Adds type-specific requirement fields and lifecycle review/approval stamps to item_revision,
-- and the first-class typed trace_link table. Never edit an applied migration — this is additive.

-- ---------------------------------------------------------------------------
-- Type-specific revision fields (DATA-MODEL §3a–3c) + lifecycle stamps (§3)
-- All nullable: only the columns relevant to an item's type are populated.
-- ---------------------------------------------------------------------------
ALTER TABLE item_revision
    ADD COLUMN acceptance_criteria   TEXT,
    ADD COLUMN un_source             VARCHAR(20)
        CHECK (un_source IN ('STAKEHOLDER', 'CLINICAL', 'REGULATORY', 'BUSINESS', 'USABILITY')),
    ADD COLUMN un_actor              VARCHAR(255),
    ADD COLUMN req_category          VARCHAR(20)
        CHECK (req_category IN ('FUNCTIONAL', 'PERFORMANCE', 'INTERFACE', 'SAFETY',
                                'SECURITY', 'USABILITY', 'REGULATORY')),
    ADD COLUMN req_priority          VARCHAR(10)
        CHECK (req_priority IN ('MUST', 'SHOULD', 'COULD')),
    ADD COLUMN software_safety_class VARCHAR(1)
        CHECK (software_safety_class IN ('A', 'B', 'C')),
    ADD COLUMN reviewed_by           UUID REFERENCES app_user (id),
    ADD COLUMN reviewed_at           TIMESTAMPTZ,
    ADD COLUMN approved_by           UUID REFERENCES app_user (id),
    ADD COLUMN approved_at           TIMESTAMPTZ;

-- ---------------------------------------------------------------------------
-- TraceLink — first-class, typed, directional relationship between two revisions
-- (DATA-MODEL §4). Audited.
-- ---------------------------------------------------------------------------
CREATE TABLE trace_link (
    id                 UUID         PRIMARY KEY,
    project_id         UUID         NOT NULL REFERENCES project (id),
    source_revision_id UUID         NOT NULL REFERENCES item_revision (id),
    target_revision_id UUID         NOT NULL REFERENCES item_revision (id),
    link_type          VARCHAR(20)  NOT NULL
        CHECK (link_type IN ('DERIVES_FROM', 'VERIFIES', 'MITIGATES',
                             'IMPLEMENTS', 'RELATES_TO', 'BRANCHED_FROM')),
    rationale          TEXT,
    suspect            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL,
    created_by         UUID         REFERENCES app_user (id),
    CONSTRAINT uq_trace_link UNIQUE (source_revision_id, target_revision_id, link_type),
    CONSTRAINT ck_trace_link_not_self CHECK (source_revision_id <> target_revision_id)
);

CREATE INDEX idx_trace_link_source ON trace_link (source_revision_id);
CREATE INDEX idx_trace_link_target ON trace_link (target_revision_id);
CREATE INDEX idx_trace_link_project ON trace_link (project_id);
