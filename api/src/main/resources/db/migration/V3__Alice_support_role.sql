-- Grant alice the SUPPORT role (keeps USER so normal app features still work).
UPDATE User
SET fachRollen = 'USER,SUPPORT',
    timestampMutiert = CURRENT_TIMESTAMP
WHERE email = 'alice@example.com';
