-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Spring AI auto-creates this table, but adding it here for clarity/documentation
-- Spring AI PGVectorStore will create 'vector_store' table automatically on startup
-- The table will look like:
--
-- CREATE TABLE IF NOT EXISTS vector_store (
--     id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
--     content     TEXT,
--     metadata    JSON,
--     embedding   VECTOR(1536)   -- 1536 dims = text-embedding-3-small
-- );
--
-- CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops);

-- Confirm extension is active
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
