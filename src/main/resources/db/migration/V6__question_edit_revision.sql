ALTER TABLE questions ADD COLUMN content_revision BIGINT NOT NULL DEFAULT 0 CHECK(content_revision >= 0);
