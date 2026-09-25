ALTER TABLE app_settings ADD COLUMN mock_duration_minutes INTEGER CHECK(mock_duration_minutes BETWEEN 1 AND 1440);
