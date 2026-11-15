CREATE TABLE mop_release (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  version VARCHAR(32) NOT NULL,
  branch_id BIGINT NOT NULL,
  rev BIGINT NOT NULL,
  compiled_model JSON NOT NULL,
  owl_object_key VARCHAR(255) NULL,
  shacl_object_key VARCHAR(255) NULL,
  quality_run_id BIGINT NOT NULL,
  cq_run_id BIGINT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
  published_by BIGINT NULL,
  published_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_release_version (project_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
