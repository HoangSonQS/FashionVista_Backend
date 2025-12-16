-- Force recreate collections with TEXT columns to avoid lower(bytea) error

DROP TABLE IF EXISTS collection_products CASCADE;
DROP TABLE IF EXISTS collections CASCADE;

CREATE TABLE collections (
    id               BIGSERIAL PRIMARY KEY,
    name             TEXT NOT NULL,
    slug             TEXT NOT NULL UNIQUE,
    description      TEXT,
    hero_image_url   TEXT,
    status           VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    visible          BOOLEAN NOT NULL DEFAULT TRUE,
    start_at         TIMESTAMP,
    end_at           TIMESTAMP,
    seo_title        TEXT,
    seo_description  TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE collection_products (
    id            BIGSERIAL PRIMARY KEY,
    collection_id BIGINT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    position      INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_collection_product UNIQUE (collection_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_collections_status ON collections(status);
CREATE INDEX IF NOT EXISTS idx_collections_visible ON collections(visible);
CREATE INDEX IF NOT EXISTS idx_collections_start_end ON collections(start_at, end_at);


