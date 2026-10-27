INSERT INTO owner (provider, provider_user_id)
VALUES ('DEV', 'dev-owner');

INSERT INTO store (owner_id, name, category)
SELECT id, '테스트 매장', '일식'
FROM owner WHERE provider = 'DEV' AND provider_user_id = 'dev-owner';