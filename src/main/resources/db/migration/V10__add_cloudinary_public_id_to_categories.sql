-- Add cloudinary_public_id column to categories table for image management
ALTER TABLE categories ADD COLUMN cloudinary_public_id VARCHAR(255);

