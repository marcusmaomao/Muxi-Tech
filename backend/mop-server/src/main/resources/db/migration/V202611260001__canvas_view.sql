CREATE TABLE mop_canvas_view (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  layout JSON NOT NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_canvas_view (branch_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mop_edit_lock (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  uid CHAR(26) NOT NULL,
  user_id BIGINT NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_edit_lock (branch_id, uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
