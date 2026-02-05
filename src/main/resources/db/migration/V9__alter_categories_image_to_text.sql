-- Change categories.image column from VARCHAR(255) to TEXT to support base64 images
ALTER TABLE categories ALTER COLUMN image TYPE TEXT;

