"""서버 없이 명령줄로 분석 실행 (확인용)
  python -m app.cli 2            # 모델 폴더가 있으면 BERT, 없으면 규칙
  python -m app.cli 2 --rules    # 비교용: 모델이 있어도 규칙으로만
"""
import json
import os
import sys

if "--rules" in sys.argv:
    os.environ["ANALYSIS_RULES_ONLY"] = "1"

from . import db, service  # noqa: E402

if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    store_id = int(args[0]) if args else 1
    with db.connect() as conn:
        result = service.run(conn, store_id)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    with db.connect() as conn:
        for r in conn.execute("""SELECT question_code, target_id, sentence FROM insight
                                 WHERE store_id = %s AND job_id IS NULL AND result = 'FLAGGED'
                                 ORDER BY question_code, id""", (store_id,)).fetchall():
            print(f"[{r['question_code']}] {r['sentence']}")
