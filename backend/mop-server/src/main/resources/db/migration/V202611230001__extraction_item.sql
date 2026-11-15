CREATE TABLE ai_extraction_item (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  job_id BIGINT NOT NULL,
  kind VARCHAR(8) NOT NULL,
  payload JSON NOT NULL,
  evidence_quote VARCHAR(1000) NOT NULL,
  confidence DECIMAL(5,4) NULL,
  reason VARCHAR(64) NULL,
  state VARCHAR(16) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
