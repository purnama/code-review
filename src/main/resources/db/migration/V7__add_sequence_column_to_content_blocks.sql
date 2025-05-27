-- Add sequence column to content_blocks table
ALTER TABLE content_blocks ADD COLUMN IF NOT EXISTS sequence INT;
-- Set default value of 0 for existing records
UPDATE content_blocks SET sequence = 0 WHERE sequence IS NULL;
-- Make sequence column NOT NULL
ALTER TABLE content_blocks ALTER COLUMN sequence SET NOT NULL;

