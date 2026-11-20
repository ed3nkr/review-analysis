"""분석 서버 (FastAPI)

  uvicorn app.main:app --port 8000
  POST /stores/{store_id}/analyze   {"job_id": 12}   → 리뷰 분석 + 연계 분석 결과를 DB에 저장하고 요약을 돌려줌
"""
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from . import db, service

app = FastAPI(title="review-sales-analysis")


class AnalyzeRequest(BaseModel):
    job_id: int | None = None   # Spring이 만든 LINK_ANALYSIS 작업 id (insight.job_id 로 저장)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/stores/{store_id}/analyze")
def analyze(store_id: int, req: AnalyzeRequest | None = None):
    job_id = req.job_id if req else None
    try:
        with db.connect() as conn:
            return service.run(conn, store_id, job_id)
    except LookupError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except service.AnalysisError as e:
        raise HTTPException(status_code=400, detail=str(e))
