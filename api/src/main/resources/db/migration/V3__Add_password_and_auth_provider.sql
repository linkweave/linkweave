-- Add password, authProvider and role columns for form-based registration

ALTER TABLE User ADD COLUMN password VARCHAR(60);
ALTER TABLE User ADD COLUMN authProvider VARCHAR(30);

-- Add columns to audit table
ALTER TABLE User_AUD ADD COLUMN password VARCHAR(60);
ALTER TABLE User_AUD ADD COLUMN authProvider VARCHAR(30);

-- Update seed users with bcrypt-hashed passwords for form-based auth
-- Passwords: 'alice' and 'test' respectively
UPDATE User SET password = '$2a$10$6FKYsHA3cdburE.iyfsXmeB0tN3uvZH6hQRonUiIjWV3kDgpJQNe2', authProvider = 'FORM' WHERE email = 'alice@example.com';
UPDATE User SET password = '$2a$10$v/mD2/g2FOs/sFRf6KrLpeh/iqtciwVSuCSzpYKjT1UCB809c5HtW', authProvider = 'FORM' WHERE email = 'test@example.com';
