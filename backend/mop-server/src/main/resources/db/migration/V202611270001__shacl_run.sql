CREATE TABLE sem_run (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  rev BIGINT NULL,
  kind VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  score DECIMAL(5,2) NULL,
  summary JSON NULL,
  report_key VARCHAR(255) NULL,
  started_at DATETIME(3) NOT NULL,
  finished_at DATETIME(3) NULL,
  KEY idx_sem_run_branch (tenant_id, branch_id, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sem_issue (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  check_code VARCHAR(64) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  element_uid CHAR(26) NULL,
  focus_node VARCHAR(512) NULL,
  path VARCHAR(512) NULL,
  message VARCHAR(1000) NULL,
  state VARCHAR(16) NOT NULL,
  ignore_reason VARCHAR(500) NULL,
  KEY idx_sem_issue_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
