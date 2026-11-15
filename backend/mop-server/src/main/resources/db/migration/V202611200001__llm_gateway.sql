CREATE TABLE ai_model (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  model_name VARCHAR(128) NOT NULL,
  vendor VARCHAR(32) NOT NULL,
  base_url VARCHAR(500) NOT NULL,
  api_key_cipher VARCHAR(700) NULL,
  rpm_limit INT NOT NULL DEFAULT 60,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ai_model_name (tenant_id, model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_usage (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  model_id BIGINT NOT NULL,
  prompt_tokens INT NOT NULL DEFAULT 0,
  completion_tokens INT NOT NULL DEFAULT 0,
  latency_ms INT NOT NULL,
  success TINYINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  KEY idx_ai_usage_model (model_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
