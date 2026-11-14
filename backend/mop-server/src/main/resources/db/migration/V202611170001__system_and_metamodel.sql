ALTER TABLE sys_user ADD COLUMN dept_id BIGINT NULL AFTER display_name;

CREATE TABLE sys_refresh_token (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  token_id CHAR(36) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  revoked TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_refresh_token (token_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_dept (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  name VARCHAR(64) NOT NULL,
  sort_no INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_post (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_post_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_menu (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  name VARCHAR(64) NOT NULL,
  path VARCHAR(128) NULL,
  menu_type VARCHAR(16) NOT NULL,
  permission VARCHAR(128) NULL,
  sort_no INT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_menu (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  created_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_dict_type (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_dict_type (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_dict_item (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  type_id BIGINT NOT NULL,
  label VARCHAR(64) NOT NULL,
  item_value VARCHAR(128) NOT NULL,
  sort_no INT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_config (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  config_key VARCHAR(128) NOT NULL,
  config_value VARCHAR(1000) NOT NULL,
  remark VARCHAR(255) NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_config_key (tenant_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_notice (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  title VARCHAR(128) NOT NULL,
  content VARCHAR(2000) NOT NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_oper_log (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  actor_id BIGINT NULL,
  title VARCHAR(64) NOT NULL,
  action VARCHAR(64) NOT NULL,
  success TINYINT NOT NULL,
  detail VARCHAR(500) NULL,
  trace_id VARCHAR(64) NULL,
  created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mop_project (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  namespace VARCHAR(255) NOT NULL,
  description VARCHAR(1000) NULL,
  head_rev BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_project_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mop_branch (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  head_rev BIGINT NOT NULL DEFAULT 0,
  protected TINYINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_by BIGINT NULL,
  created_at DATETIME(3) NULL,
  UNIQUE KEY uk_branch (project_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mop_commit (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  rev BIGINT NOT NULL,
  message VARCHAR(500) NOT NULL,
  change_count INT NOT NULL DEFAULT 0,
  author_id BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_commit_rev (project_id, rev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mop_element (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  uid CHAR(26) NOT NULL,
  element_type VARCHAR(32) NOT NULL,
  api_name VARCHAR(128) NULL,
  owner_uid CHAR(26) NULL,
  rev_start BIGINT NOT NULL,
  rev_end BIGINT NULL,
  tombstone TINYINT NOT NULL DEFAULT 0,
  payload JSON NOT NULL,
  content_hash CHAR(64) NOT NULL,
  KEY idx_el_current (branch_id, rev_end, element_type),
  KEY idx_el_uid (project_id, uid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mop_element_ref (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  from_uid CHAR(26) NOT NULL,
  to_uid CHAR(26) NOT NULL,
  ref_type VARCHAR(32) NOT NULL,
  rev_start BIGINT NOT NULL,
  rev_end BIGINT NULL,
  KEY idx_ref_to (branch_id, rev_end, to_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mop_change (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  commit_id BIGINT NOT NULL,
  uid CHAR(26) NOT NULL,
  element_type VARCHAR(32) NOT NULL,
  op VARCHAR(8) NOT NULL,
  before_json JSON NULL,
  after_json JSON NULL,
  KEY idx_change_commit (commit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
