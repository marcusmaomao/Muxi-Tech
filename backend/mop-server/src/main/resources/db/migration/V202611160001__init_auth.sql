CREATE TABLE sys_tenant (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  uid CHAR(26) NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_tenant_code (code),
  UNIQUE KEY uk_tenant_uid (uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  uid CHAR(26) NOT NULL,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user_name (tenant_id, username),
  UNIQUE KEY uk_user_uid (uid),
  KEY idx_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  uid CHAR(26) NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_role_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_login_log (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  username VARCHAR(64) NOT NULL,
  success TINYINT NOT NULL,
  message VARCHAR(255) NULL,
  ip VARCHAR(64) NULL,
  trace_id VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL,
  KEY idx_login_user (username, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
