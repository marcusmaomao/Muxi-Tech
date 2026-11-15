CREATE TABLE ai_job (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  model_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  stage VARCHAR(16) NOT NULL,
  prompt_version VARCHAR(16) NOT NULL,
  error VARCHAR(1000) NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  finished_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_evidence (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  block_no INT NOT NULL,
  content MEDIUMTEXT NOT NULL,
  content_hash CHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_candidate (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  candidate_key VARCHAR(255) NOT NULL,
  element_type VARCHAR(32) NOT NULL,
  payload JSON NOT NULL,
  evidence_quote VARCHAR(1000) NOT NULL,
  block_no INT NOT NULL,
  confidence DECIMAL(5,4) NULL,
  state VARCHAR(16) NOT NULL,
  adopted_uid CHAR(26) NULL,
  message VARCHAR(500) NULL,
  UNIQUE KEY uk_ai_candidate (job_id, candidate_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_job_event (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  seq_no INT NOT NULL,
  stage VARCHAR(16) NOT NULL,
  message VARCHAR(500) NOT NULL,
  created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
