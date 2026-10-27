-- 로그인 제공자: 카카오·구글 → 네이버
ALTER TABLE owner DROP CONSTRAINT owner_provider_check;
ALTER TABLE owner ADD CONSTRAINT owner_provider_check
    CHECK (provider IN ('NAVER', 'DEV'));

-- 매장 확인(네이버 검색 API)에서 받은 전화번호. 나중에 플레이스 ID 매칭에 사용
ALTER TABLE store ADD COLUMN phone VARCHAR(20);