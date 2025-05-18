-- Add active column to confluence_urls table with default value true
ALTER TABLE confluence_urls ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

-- Add last_fetched column if it doesn't exist already
ALTER TABLE confluence_urls ADD COLUMN IF NOT EXISTS last_fetched TIMESTAMP;
