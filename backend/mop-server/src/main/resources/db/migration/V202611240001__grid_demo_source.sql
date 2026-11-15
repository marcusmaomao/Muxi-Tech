CREATE TABLE src_line (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  from_station_id VARCHAR(32) NOT NULL,
  updated_at VARCHAR(32) NOT NULL,
  CONSTRAINT fk_src_line_station FOREIGN KEY (from_station_id) REFERENCES src_station (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE src_inspection (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  transformer_id VARCHAR(32) NOT NULL,
  note VARCHAR(255) NOT NULL,
  updated_at VARCHAR(32) NOT NULL,
  CONSTRAINT fk_src_inspection_equipment FOREIGN KEY (transformer_id) REFERENCES src_equipment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO src_line (id, name, from_station_id, updated_at) VALUES ('l1', '城东线', 's1', '2026-01-01 00:00:00');
INSERT INTO src_inspection (id, transformer_id, note, updated_at) VALUES ('i1', 'e1', '运行正常', '2026-01-01 00:00:00');
