# ai-worker

Python 3.11+ FastAPI。不做登录鉴权，只接受 Java 编排调用。

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8090
```

当前：`GET /health`、`POST /v1/jobs/clean` 空实现。真实去水 / ABSA 按 W2 后续日期再写。
