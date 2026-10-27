-- =====================================================================
-- V1: 초기 스키마 (ERD 8개 테이블)
--   기준 데이터: owner, store, menu_group, menu
--   원본+가공 데이터: sales_record, review
--   처리 과정: job
--   해석 결과: insight (9개 질문 판정 + 점주 평가)
-- =====================================================================

-- owner: 점주 계정 (카카오·구글 소셜 로그인, 개발 중엔 DEV)
CREATE TABLE owner (
                       id                BIGSERIAL    PRIMARY KEY,
                       provider          VARCHAR(10)  NOT NULL CHECK (provider IN ('KAKAO', 'GOOGLE', 'DEV')),
                       provider_user_id  VARCHAR(100) NOT NULL,
                       email             VARCHAR(255),
                       created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       CONSTRAINT uq_owner_provider UNIQUE (provider, provider_user_id)
);

-- store: 매장 + 네이버 플레이스 연결 + 매장별 분석 설정
CREATE TABLE store (
                       id                 BIGSERIAL    PRIMARY KEY,
                       owner_id           BIGINT       NOT NULL REFERENCES owner(id) ON DELETE CASCADE,
                       name               VARCHAR(100) NOT NULL,
                       category           VARCHAR(50),
                       place_id           VARCHAR(30),
                       place_name         VARCHAR(100),
                       address            VARCHAR(255),
                       slot_cutoff        TIME         NOT NULL DEFAULT '16:00',   -- 이전 = 점심, 이후 = 저녁
                       last_collected_at  TIMESTAMPTZ
);
CREATE INDEX idx_store_owner ON store(owner_id);

-- menu_group: 연계 분석 단위(메뉴군) + 리뷰 별칭
CREATE TABLE menu_group (
                            id        BIGSERIAL    PRIMARY KEY,
                            store_id  BIGINT       NOT NULL REFERENCES store(id) ON DELETE CASCADE,
                            name      VARCHAR(50)  NOT NULL,
                            aliases   TEXT[]       NOT NULL DEFAULT '{}',
                            CONSTRAINT uq_menu_group_name UNIQUE (store_id, name)
);

-- menu: POS 메뉴명 1개 = 1행
CREATE TABLE menu (
                      id               BIGSERIAL    PRIMARY KEY,
                      store_id         BIGINT       NOT NULL REFERENCES store(id) ON DELETE CASCADE,
                      menu_group_id    BIGINT       NOT NULL REFERENCES menu_group(id),
                      pos_name         VARCHAR(200) NOT NULL,
                      normalized_name  VARCHAR(200) NOT NULL,
                      variant          VARCHAR(30),
                      topping          VARCHAR(50),
                      parse_rule       VARCHAR(10)  NOT NULL DEFAULT 'RULE'
                          CHECK (parse_rule IN ('RULE', 'AS_IS', 'MANUAL')),
                      CONSTRAINT uq_menu_normalized UNIQUE (store_id, normalized_name)
);
CREATE INDEX idx_menu_group ON menu(menu_group_id);

-- job: 비동기 작업
CREATE TABLE job (
                     id               BIGSERIAL    PRIMARY KEY,
                     store_id         BIGINT       NOT NULL REFERENCES store(id) ON DELETE CASCADE,
                     type             VARCHAR(20)  NOT NULL
                         CHECK (type IN ('SALES_UPLOAD', 'REVIEW_COLLECT', 'LINK_ANALYSIS')),
                     status           VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED')),
                     file_path        VARCHAR(500),
                     period_start     DATE,
                     period_end       DATE,
                     processed_count  INTEGER      NOT NULL DEFAULT 0,
                     error_count      INTEGER      NOT NULL DEFAULT 0,
                     error_detail     JSONB,
                     requested_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
                     started_at       TIMESTAMPTZ,
                     finished_at      TIMESTAMPTZ
);
CREATE INDEX idx_job_store_type ON job(store_id, type, requested_at DESC);

-- sales_record: 영수증의 메뉴 1줄 = 1행
CREATE TABLE sales_record (
                              id          BIGSERIAL    PRIMARY KEY,
                              store_id    BIGINT       NOT NULL REFERENCES store(id) ON DELETE CASCADE,
                              job_id      BIGINT       REFERENCES job(id) ON DELETE SET NULL,
                              menu_id     BIGINT       REFERENCES menu(id) ON DELETE SET NULL,
                              bill_no     VARCHAR(30)  NOT NULL,
                              line_no     INTEGER      NOT NULL,
                              menu_name   VARCHAR(200) NOT NULL,
                              sold_at     TIMESTAMPTZ  NOT NULL,
                              quantity    INTEGER      NOT NULL,
                              amount      INTEGER      NOT NULL,
                              status      VARCHAR(10)  NOT NULL DEFAULT 'NORMAL' CHECK (status IN ('NORMAL', 'RETURN')),
                              CONSTRAINT uq_sales_line UNIQUE (store_id, bill_no, sold_at, line_no)
);
CREATE INDEX idx_sales_store_sold ON sales_record(store_id, sold_at);
CREATE INDEX idx_sales_menu ON sales_record(menu_id);

-- review: 리뷰 1건 = 1행 (원본 / 분석 결과)
CREATE TABLE review (
                        id                 BIGSERIAL    PRIMARY KEY,
                        store_id           BIGINT       NOT NULL REFERENCES store(id) ON DELETE CASCADE,
                        job_id             BIGINT       REFERENCES job(id) ON DELETE SET NULL,
                        source             VARCHAR(20)  NOT NULL DEFAULT 'NAVER_PLACE',
                        dedup_key          VARCHAR(64)  NOT NULL,
    -- 원본 (수집 후 변경 안 함)
                        content            TEXT,
                        rating             INTEGER      CHECK (rating BETWEEN 1 AND 5),
                        written_at         DATE,
                        visited_at         DATE,
                        visit_slot_tag     VARCHAR(20),
                        author_hash        VARCHAR(64),
    -- 분석 결과 (재분석 시 덮어씀)
                        reliability        DOUBLE PRECISION,
                        excluded           BOOLEAN      NOT NULL DEFAULT FALSE,
                        exclude_reason     VARCHAR(30),
                        neg_types          TEXT[]       NOT NULL DEFAULT '{}',
                        neg_probs          JSONB,
                        menu_group_ids     BIGINT[]     NOT NULL DEFAULT '{}',
                        praised_group_ids  BIGINT[]     NOT NULL DEFAULT '{}',
                        blamed_group_ids   BIGINT[]     NOT NULL DEFAULT '{}',
                        time_slot          VARCHAR(10)  CHECK (time_slot IN ('LUNCH', 'DINNER')),
                        model_version      VARCHAR(50),
                        analyzed_at        TIMESTAMPTZ,
                        CONSTRAINT uq_review_dedup UNIQUE (store_id, dedup_key)
);
CREATE INDEX idx_review_store_visited ON review(store_id, visited_at);
CREATE INDEX idx_review_menu_groups ON review USING GIN (menu_group_ids);

-- insight: 연계 분석 결과 (질문 1개 × 대상 1개 = 1행)
CREATE TABLE insight (
                         id             BIGSERIAL    PRIMARY KEY,
                         store_id       BIGINT       NOT NULL REFERENCES store(id) ON DELETE CASCADE,
                         job_id         BIGINT       REFERENCES job(id) ON DELETE SET NULL,
    -- 판정 (시스템)
                         question_code  VARCHAR(5)   NOT NULL
                             CHECK (question_code IN ('Q1','Q2','Q3','Q4','Q5','Q6','Q7','Q8','Q9','R1')),
                         target_type    VARCHAR(15)  NOT NULL
                             CHECK (target_type IN ('STORE', 'MENU_GROUP', 'SLOT', 'MONTH', 'WEEK', 'NEG_TYPE')),
                         target_id      VARCHAR(50),
                         result         VARCHAR(15)  NOT NULL
                             CHECK (result IN ('FLAGGED', 'NORMAL', 'INSUFFICIENT')),
                         evidence       JSONB        NOT NULL,
                         p_value        DOUBLE PRECISION,
                         sentence       TEXT         NOT NULL,
                         llm_sentence   TEXT,
                         llm_status     VARCHAR(10)  CHECK (llm_status IN ('OK', 'FALLBACK', 'SKIPPED')),
    -- 점주 평가
                         eval_new       BOOLEAN,
                         eval_correct   BOOLEAN,
                         eval_action    BOOLEAN,
                         eval_comment   TEXT,
                         evaluated_at   TIMESTAMPTZ
);
CREATE INDEX idx_insight_store_job ON insight(store_id, job_id, question_code);