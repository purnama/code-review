-- Add sequence column to content_blocks table
ALTER TABLE content_blocks ADD COLUMN IF NOT EXISTS sequence INTEGER;
