-- Seed embedded users for form-based auth

INSERT INTO User (id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version, aktiv, email, fachRollen, nachname, vorname)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'system',
    'system',
    0,
    1,
    'alice@example.com',
    'USER',
    'User',
    'Alice'
);

INSERT INTO User (id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version, aktiv, email, fachRollen, nachname, vorname)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'system',
    'system',
    0,
    1,
    'test@example.com',
    'USER',
    'User',
    'Test'
);


UPDATE User SET password = '$2a$10$6FKYsHA3cdburE.iyfsXmeB0tN3uvZH6hQRonUiIjWV3kDgpJQNe2', authProvider = 'FORM' WHERE email = 'alice@example.com';
UPDATE User SET password = '$2a$10$v/mD2/g2FOs/sFRf6KrLpeh/iqtciwVSuCSzpYKjT1UCB809c5HtW', authProvider = 'FORM' WHERE email = 'test@example.com';

-- Default user settings for all users (consolidated from former V14).
INSERT INTO UserSettings (user_id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version, offlineCachingEnabled)
SELECT id, timestampErstellt, timestampMutiert, userErstellt, userMutiert, version, TRUE
FROM User;
