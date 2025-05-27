-- Add confluence_url_id column to content_blocks table
ALTER TABLE content_blocks ADD COLUMN IF NOT EXISTS confluence_url_id BIGINT;

-- Add foreign key constraint
ALTER TABLE content_blocks
ADD CONSTRAINT fk_content_blocks_confluence_url
FOREIGN KEY (confluence_url_id)
REFERENCES confluence_urls (id)
ON DELETE SET NULL;

