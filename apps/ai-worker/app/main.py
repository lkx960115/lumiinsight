"""灯鉴 Worker。清洗用标准库，避免本机 FastAPI/pydantic 卡住。"""

from __future__ import annotations

import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.clean import clean_reviews

HOST = "127.0.0.1"
PORT = 8090


class WorkerHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _send(self, code: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        if urlparse(self.path).path == "/health":
            self._send(200, {"status": "up"})
            return
        self._send(404, {"code": "NOT_FOUND", "message": "not found"})

    def do_POST(self) -> None:
        if urlparse(self.path).path != "/v1/jobs/clean":
            self._send(404, {"code": "NOT_FOUND", "message": "not found"})
            return
        length = int(self.headers.get("Content-Length", "0") or "0")
        raw = self.rfile.read(length) if length else b"{}"
        try:
            data = json.loads(raw.decode("utf-8") or "{}")
        except json.JSONDecodeError:
            self._send(400, {"code": "BAD_JSON", "message": "请求体不是 JSON"})
            return
        reviews = data.get("reviews") or []
        if not isinstance(reviews, list):
            self._send(400, {"code": "BAD_REVIEWS", "message": "reviews 必须是数组"})
            return
        result = clean_reviews(reviews)
        self._send(
            200,
            {
                "code": "0",
                "message": result["message"],
                "jobId": data.get("jobId"),
                "projectId": data.get("projectId"),
                "cleaned": result["cleaned"],
                "stats": result["stats"],
                "items": result["items"],
            },
        )


def main() -> None:
    httpd = ThreadingHTTPServer((HOST, PORT), WorkerHandler)
    print("lumiinsight-ai-worker listening on http://%s:%s" % (HOST, PORT), flush=True)
    httpd.serve_forever()


if __name__ == "__main__":
    main()
