# 분석 서버 (Python · FastAPI)

리뷰 분석 파이프라인(논문 3.3.3)과 리뷰-매출 연계 분석(3.3.4, 9개 질문 + 반복 불만)을 하고,
결과를 같은 PostgreSQL 의 `review`(분석 칸)와 `insight` 테이블에 저장한다.

## 리뷰 분석 파이프라인
1. **규칙 기반 1차 필터** — URL·광고, 이벤트 문구, 다른 가게, 의미 없는 글, 같은 작성자 복붙 → `exclude_reason`
2. **노이즈 분류 모델 (KLUE-BERT)** — 정상일 확률 → `reliability`, 0.3 미만이면 `LOW_RELIABILITY` 로 제외
3. **부정 유형 분류 모델 (KLUE-BERT, 멀티라벨)** — 6개 유형 확률 → `neg_probs`, 0.5 이상 → `neg_types`
4. **메뉴·시간대 태깅** — 메뉴군 이름·별칭(DB)으로 언급 메뉴, 메뉴 문장별 불만(모델)·칭찬 → `menu/praised/blamed_group_ids`, `time_slot`

모델 폴더가 없으면 2·3단계는 규칙(rule-v1)으로 대신한다. 어떤 방식으로 돌았는지는 `review.model_version` 에 남는다.

| 파일 | 하는 일 |
|---|---|
| `app/rules.py` | 규칙 (1차 필터, 모델이 없을 때의 대체 규칙) |
| `app/models.py` | KLUE-BERT 모델 2개 읽기·추론 (`models/noise`, `models/negtype`) |
| `app/review_analysis.py` | 위 파이프라인 |
| `app/link_analysis.py` | 9개 질문 판정 + 근거 수치 + 문장 |
| `app/db.py` · `app/service.py` | DB 읽기·쓰기, 한 번 실행 |
| `app/main.py` | FastAPI: `POST /stores/{id}/analyze` |
| `app/cli.py` | 서버 없이 확인: `python -m app.cli <매장id>` (`--rules` 로 규칙만) |
| `training/` | 학습 노트북(Colab), 학습셋·평가셋, 학습셋 생성 스크립트 |

## 실행
```bash
python -m venv .venv && source .venv/Scripts/activate      # Windows Git Bash
pip install -r requirements.txt
pip install torch --index-url https://download.pytorch.org/whl/cpu
python -m app.cli 3              # 모델 있으면 KLUE-BERT, 없으면 규칙
python -m app.cli 3 --rules      # 비교용
uvicorn app.main:app --port 8000
```
DB 주소 기본값 `postgresql://review:review_local_pw@localhost:5432/review_analysis` (다르면 `DATABASE_URL`).

## 모델
`training/review_models_klue_bert.ipynb` 를 Colab(T4 GPU)에서 실행 → `models.zip` 을 `analysis/` 에서 풀면
`analysis/models/noise/`, `analysis/models/negtype/` 가 생긴다. 용량이 커서 Git 에 올리지 않는다 (`.gitignore: analysis/models/`).
