"""분석 한 번 실행: DB에서 읽기 → 리뷰 분석 → 연계 분석 → 저장 (FastAPI·명령줄 공용)"""
from . import db, link_analysis, models, review_analysis


class AnalysisError(Exception):
    """점주에게 그대로 보여 줄 수 있는 이유"""


def run(conn, store_id, job_id=None):
    store, groups, sales, reviews = db.load(conn, store_id)
    if sales.empty:
        raise AnalysisError("매출이 없어요. 매출 CSV를 먼저 올려 주세요.")
    if reviews.empty:
        raise AnalysisError("리뷰가 없어요. 리뷰를 먼저 모아 주세요.")
    if not groups:
        raise AnalysisError("메뉴군이 없어요. 매출을 올리면 자동으로 만들어져요.")

    noise_model, type_model = models.load()
    rv = review_analysis.analyze(reviews, groups, noise_model, type_model)
    db.save_reviews(conn, rv)

    insights, summary = link_analysis.run(sales, rv, groups, store["slot_cutoff"])
    db.save_insights(conn, store_id, job_id, insights)
    conn.commit()

    return dict(store_id=store_id, job_id=job_id, model=rv["model_version"].iloc[0], **summary,
                reviews_analyzed=len(rv), reviews_excluded=int(rv["excluded"].sum()),
                reviews_negative=int((rv["sentiment"] == "NEGATIVE").sum()),
                reviews_positive=int((rv["sentiment"] == "POSITIVE").sum()),
                insights=len(insights),
                by_result={k: sum(1 for i in insights if i["result"] == k) for k in ("FLAGGED", "NORMAL", "INSUFFICIENT")})
