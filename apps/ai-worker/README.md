# ai-worker

Python 清洗 Worker。不做登录鉴权，只接受 Java 编排调用。

本机部分 Mac（Python 3.13 + pydantic 底层库）会出现 `uvicorn` / `pip` 卡住无输出。空实现用标准库启动，**不需要**虚拟环境和 `pip install`。

```bash
cd apps/ai-worker
python3 app/main.py
```

看到 `lumiinsight-ai-worker listening on http://127.0.0.1:8090` 即可。

当前：`GET /health`、`POST /v1/jobs/clean` 空实现。真实去水 / ABSA 按 W2 后续日期再写。
