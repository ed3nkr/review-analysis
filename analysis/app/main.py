from fastapi import FastAPI

app = FastAPI(title="review-analysis")


@app.get("/health")
def health():
    return {"status": "ok"}
