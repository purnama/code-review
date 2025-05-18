-- Add confluence_url_id column to content_blocks table if missing
ALTER TABLE content_blocks ADD COLUMN IF NOT EXISTS confluence_url_id BIGINT;

-- Add foreign key constraint if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_content_blocks_confluence_url' 
        AND table_name = 'content_blocks'
    ) THEN
        ALTER TABLE content_blocks 
        ADD CONSTRAINT fk_content_blocks_confluence_url
        FOREIGN KEY (confluence_url_id) 
        REFERENCES confluence_urls (id);
    END IF;
END $$;
