"""DB 읽기·쓰기 (psycopg 3). 스키마는 Spring(Flyway)이 만들고, 여기서는 읽고 분석 칸만 채운다."""
import os

import pandas as pd
import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb

DEFAULT_URL = "postgresql://review:review_local_pw@localhost:5432/review_analysis"

SALES_COLUMNS = ["bill_no", "sold_at", "quantity", "amount", "status", "menu_group_id", "topping"]
REVIEW_COLUMNS = ["id", "content", "rating", "day", "visit_slot_tag", "author_hash"]


def connect():
    return psycopg.connect(os.environ.get("DATABASE_URL", DEFAULT_URL), row_factory=dict_row)


def load(conn, store_id):
    store = conn.execute("SELECT id, name, slot_cutoff FROM store WHERE id = %s", (store_id,)).fetchone()
    if store is None:
        raise LookupError(f"매장 {store_id} 이(가) 없어요")
    groups = conn.execute(
        "SELECT id, name, aliases FROM menu_group WHERE store_id = %s ORDER BY id", (store_id,)).fetchall()
    sales = conn.execute("""
        SELECT s.bill_no, s.sold_at, s.quantity, s.amount, s.status, m.menu_group_id, m.topping
        FROM sales_record s
        LEFT JOIN menu m ON m.id = s.menu_id
        WHERE s.store_id = %s
        """, (store_id,)).fetchall()
    reviews = conn.execute("""
        SELECT id, content, rating, coalesce(visited_at, written_at) AS day, visit_slot_tag, author_hash
        FROM review
        WHERE store_id = %s
        ORDER BY id
        """, (store_id,)).fetchall()
    return (store, groups,
            pd.DataFrame(sales, columns=SALES_COLUMNS),
            pd.DataFrame(reviews, columns=REVIEW_COLUMNS))


def save_reviews(conn, rv):
    """review 테이블의 '분석 결과' 칸만 덮어쓴다 (원본 칸은 건드리지 않음)"""
    rows = [(bool(r.excluded), r.exclude_reason if isinstance(r.exclude_reason, str) else None,
             float(r.reliability), list(r.neg_types),
             Jsonb(r.neg_probs) if isinstance(r.neg_probs, dict) else None,
             r.sentiment if isinstance(r.sentiment, str) else None,
             None if r.neg_score is None or pd.isna(r.neg_score) else float(r.neg_score),
             [int(x) for x in r.menu_group_ids], [int(x) for x in r.praised_group_ids], [int(x) for x in r.blamed_group_ids],
             r.time_slot if isinstance(r.time_slot, str) else None, r.model_version, int(r.id))
            for r in rv.itertuples()]
    with conn.cursor() as cur:
        cur.executemany("""
            UPDATE review
            SET excluded = %s, exclude_reason = %s, reliability = %s, neg_types = %s::text[],
                neg_probs = %s, sentiment = %s, neg_score = %s,
                menu_group_ids = %s::bigint[], praised_group_ids = %s::bigint[], blamed_group_ids = %s::bigint[],
                time_slot = %s, model_version = %s, analyzed_at = now()
            WHERE id = %s
            """, rows)


def save_insights(conn, store_id, job_id, insights):
    """같은 작업(job)의 이전 결과를 지우고 새로 저장"""
    conn.execute("DELETE FROM insight WHERE store_id = %s AND job_id IS NOT DISTINCT FROM %s", (store_id, job_id))
    with conn.cursor() as cur:
        cur.executemany("""
            INSERT INTO insight (store_id, job_id, question_code, target_type, target_id, result, evidence, p_value, sentence)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
            """, [(store_id, job_id, i["question_code"], i["target_type"], i["target_id"], i["result"],
                   Jsonb(i["evidence"]), i["p_value"], i["sentence"]) for i in insights])
