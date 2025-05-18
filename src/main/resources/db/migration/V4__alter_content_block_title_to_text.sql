-- Change title column from VARCHAR(255) to TEXT in content_blocks table
ALTER TABLE content_blocks ALTER COLUMN title TYPE TEXT;