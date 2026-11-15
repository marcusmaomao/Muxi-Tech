CREATE TABLE rt_mapping (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  datasource_id BIGINT NOT NULL,
  object_uid CHAR(26) NOT NULL,
  version_no INT NOT NULL,
  definition JSON NOT NULL,
  created_by BIGINT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_rt_mapping_ver (tenant_id, branch_id, datasource_id, object_uid, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rt_sync_cursor (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  datasource_id BIGINT NOT NULL,
  object_uid CHAR(26) NOT NULL,
  cursor_value VARCHAR(255) NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_rt_sync_cursor (tenant_id, branch_id, datasource_id, object_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rt_sync_run (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  mapping_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  mode VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  inserted_count INT NOT NULL,
  updated_count INT NOT NULL,
  link_count INT NOT NULL,
  message VARCHAR(1000) NULL,
  started_at DATETIME(3) NOT NULL,
  finished_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rt_sync_log (
  id BIGINT NOT NULL PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  message VARCHAR(500) NOT NULL,
  created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE src_station (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  updated_at VARCHAR(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE src_equipment (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  station_id VARCHAR(32) NOT NULL,
  updated_at VARCHAR(32) NOT NULL,
  CONSTRAINT fk_src_equipment_station FOREIGN KEY (station_id) REFERENCES src_station (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO src_station (id, name, updated_at) VALUES ('s1', '城东站', '2026-01-01 00:00:00');
INSERT INTO src_equipment (id, name, station_id, updated_at) VALUES ('e1', '一号变压器', 's1', '2026-01-01 00:00:00');
