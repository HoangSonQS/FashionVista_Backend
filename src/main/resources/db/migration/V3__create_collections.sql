-- Collections schema (public + admin)
CREATE TABLE IF NOT EXISTS collections (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    slug             VARCHAR(255) NOT NULL UNIQUE,
    description      TEXT,
    hero_image_url   TEXT,
    status           VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    visible          BOOLEAN NOT NULL DEFAULT TRUE,
    start_at         TIMESTAMP,
    end_at           TIMESTAMP,
    seo_title        VARCHAR(255),
    seo_description  TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS collection_products (
    id            BIGSERIAL PRIMARY KEY,
    collection_id BIGINT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    position      INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_collection_product UNIQUE (collection_id, product_id)
);

-- Ensure column types are text/varchar (avoid bytea) for lower()/like queries
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'collections' AND column_name = 'name') THEN
        ALTER TABLE collections ALTER COLUMN name TYPE VARCHAR(255) USING name::text;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'collections' AND column_name = 'slug') THEN
        ALTER TABLE collections ALTER COLUMN slug TYPE VARCHAR(255) USING slug::text;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'collections' AND column_name = 'description') THEN
        ALTER TABLE collections ALTER COLUMN description TYPE TEXT USING description::text;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'collections' AND column_name = 'hero_image_url') THEN
        ALTER TABLE collections ALTER COLUMN hero_image_url TYPE TEXT USING hero_image_url::text;
    END IF;
END $$;

-- Indexes for filtering/search
CREATE INDEX IF NOT EXISTS idx_collections_status ON collections(status);
CREATE INDEX IF NOT EXISTS idx_collections_visible ON collections(visible);
CREATE INDEX IF NOT EXISTS idx_collections_start_end ON collections(start_at, end_at);


