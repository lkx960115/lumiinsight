from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(title="lumiinsight-ai-worker", version="0.1.0")


class CleanRequest(BaseModel):
    job_id: int = Field(alias="jobId")
    project_id: int = Field(alias="projectId")
    stage: str = "CLEAN"

    model_config = {"populate_by_name": True}


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "up"}


@app.post("/v1/jobs/clean")
def clean(req: CleanRequest) -> dict[str, object]:
    return {
        "code": "0",
        "message": "清洗空实现完成，尚未改写评论",
        "jobId": req.job_id,
        "projectId": req.project_id,
        "cleaned": 0,
    }
