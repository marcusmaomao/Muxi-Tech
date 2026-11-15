ALTER TABLE mop_project
  ADD COLUMN require_cq_link TINYINT NOT NULL DEFAULT 1 AFTER owner_user_id;
