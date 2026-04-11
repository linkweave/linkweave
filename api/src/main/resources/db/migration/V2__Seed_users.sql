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
