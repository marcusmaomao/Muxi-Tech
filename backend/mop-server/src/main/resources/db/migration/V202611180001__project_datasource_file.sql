ALTER TABLE mop_project
  ADD COLUMN domain VARCHAR(128) NULL AFTER name,
  ADD COLUMN scope_include VARCHAR(1000) NULL AFTER namespace,
  ADD COLUMN scope_exclude VARCHAR(1000) NULL AFTER scope_include,
  ADD COLUMN owner_user_id BIGINT NULL AFTER description;

CREATE TABLE mop_project_member (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  member_role VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_project_member (project_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mop_datasource (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  name VARCHAR(128) NOT NULL,
  db_type VARCHAR(16) NOT NULL,
  host VARCHAR(255) NOT NULL,
  port INT NOT NULL,
  database_name VARCHAR(128) NOT NULL,
  username VARCHAR(128) NOT NULL,
  password_cipher VARCHAR(512) NOT NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_datasource_name (tenant_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mop_file (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  category VARCHAR(64) NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(128) NULL,
  size_bytes BIGINT NOT NULL,
  object_key VARCHAR(255) NOT NULL,
  text_status VARCHAR(16) NOT NULL,
  extracted_text MEDIUMTEXT NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
