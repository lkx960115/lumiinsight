# ai-worker

Python 清洗 Worker。不做登录鉴权，只接受 Java 编排调用。

本机用标准库启动，**不需要** `pip install`：

```bash
cd apps/ai-worker
python3 app/main.py
```

看到 `lumiinsight-ai-worker listening on http://127.0.0.1:8090` 即可。

当前：`GET /health`、`POST /v1/jobs/clean`、`POST /v1/jobs/analyze`（情感 + 方面，方面名必须落在词典或「其它」；无 Key 时用词典规则）。
