-- Add embedding column to content_blocks table
ALTER TABLE content_blocks ADD COLUMN IF NOT EXISTS embedding vector(1536);