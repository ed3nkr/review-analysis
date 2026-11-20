-- 홍대점 더미 데이터용: 점주가 메뉴 화면에서 할 "메뉴군 합치기 + 별칭"을 한 번에 (매장 id 를 -v store=번호 로 넘김)
-- docker exec -i review-db psql -U review -d review_analysis -v store=3 < tools/hongdae_menu_setup.sql
-- 점주가 메뉴 화면에서 하는 일(옮기기로 합치기 + 별칭)을 SQL로 흉내
CREATE TEMP TABLE mv(src text, dst text);
INSERT INTO mv VALUES
 ('카라이마제소바','마제소바'),
 ('에비텐 어묵우동','우동'),('카라이에비텐 어묵우동','우동'),('가라아게 붓가케우동','우동'),('에비텐 붓가케우동','우동'),('미니우동 추가','우동'),
 ('키소카레','카레'),('키마카레','카레'),('미니카레','카레'),
 ('자루소바','소바(냉·자루)'),('냉소바','소바(냉·자루)'),
 ('치즈카츠','돈카츠'),('치즈모듬카츠','돈카츠'),('체다치즈카츠','돈카츠'),('에비텐모듬카츠','돈카츠'),('오꼬노미카츠','돈카츠'),
 ('스페셜가츠동','가츠동'),('에비가츠동','가츠동'),
 ('사바 온소바','온소바');
INSERT INTO menu_group (store_id, name) SELECT DISTINCT :store, dst FROM mv WHERE dst NOT IN (SELECT name FROM menu_group WHERE store_id=:store);
UPDATE menu m SET menu_group_id = (SELECT id FROM menu_group WHERE store_id=:store AND name=mv.dst), parse_rule='MANUAL'
FROM menu_group g, mv WHERE m.menu_group_id=g.id AND g.name=mv.src AND m.store_id=:store;
DELETE FROM menu_group g WHERE store_id=:store AND NOT EXISTS (SELECT 1 FROM menu WHERE menu_group_id=g.id);
UPDATE menu_group SET aliases = CASE name
  WHEN '마제소바' THEN ARRAY['마제']
  WHEN '돈카츠' THEN ARRAY['돈까스','돈가스','카츠','치즈카츠','등심카츠']
  WHEN '가츠동' THEN ARRAY['덮밥']
  WHEN '소바(냉·자루)' THEN ARRAY['자루소바','냉소바','냉모밀','소바']
  WHEN '우동' THEN ARRAY['어묵우동','붓가케우동','붓카케우동','붓가케']
  WHEN '카레' THEN ARRAY['키소카레','키마카레']
  WHEN '온소바' THEN ARRAY['고등어 온소바']
  ELSE aliases END WHERE store_id=:store;
