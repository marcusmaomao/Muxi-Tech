ALTER TABLE mop_branch
  ADD COLUMN parent_branch_id BIGINT NULL AFTER name,
  ADD COLUMN fork_rev BIGINT NULL AFTER parent_branch_id;

ALTER TABLE mop_commit
  ADD COLUMN parent_rev BIGINT NULL AFTER rev,
  ADD COLUMN merge_source_branch_id BIGINT NULL AFTER parent_rev,
  ADD COLUMN merge_source_rev BIGINT NULL AFTER merge_source_branch_id,
  ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'MANUAL' AFTER message;

ALTER TABLE mop_change
  ADD COLUMN field_paths JSON NULL AFTER after_json;

UPDATE mop_branch SET protected = 0 WHERE name = 'main' AND parent_branch_id IS NULL;

CREATE TABLE mop_merge_request (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  source_branch_id BIGINT NOT NULL,
  target_branch_id BIGINT NOT NULL,
  base_rev BIGINT NOT NULL,
  source_rev BIGINT NOT NULL,
  target_rev BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  description VARCHAR(2000) NULL,
  status VARCHAR(16) NOT NULL,
  quality_run_id BIGINT NULL,
  cq_run_id BIGINT NULL,
  conflicts JSON NULL,
  resolutions JSON NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  merged_by BIGINT NULL,
  merged_at DATETIME(3) NULL,
  KEY idx_mr_project (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mop_mr_review (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  mr_id BIGINT NOT NULL,
  reviewer_id BIGINT NULL,
  decision VARCHAR(16) NOT NULL,
  comment VARCHAR(2000) NULL,
  element_uid CHAR(26) NULL,
  created_at DATETIME(3) NOT NULL,
  KEY idx_review_mr (mr_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
