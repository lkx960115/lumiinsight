"""灯鉴 Worker。清洗用标准库，避免本机 FastAPI/pydantic 卡住。"""

from __future__ import annotations

import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.absa import analyze_reviews
from app.clean import clean_reviews

HOST = "127.0.0.1"
PORT = 8090


def read_http_body(headers: Any, rfile: Any) -> bytes:
    """Read JSON body. Java RestClient may send Transfer-Encoding: chunked without Content-Length."""
    encoding = (headers.get("Transfer-Encoding") or "").lower()
    if "chunked" in encoding:
        chunks: list[bytes] = []
        while True:
            line = rfile.readline()
            if not line:
                break
            size_token = line.strip().split(b";", 1)[0]
            try:
                size = int(size_token, 16)
            except ValueError:
                break
            if size == 0:
                while True:
                    trailer = rfile.readline()
                    if not trailer or trailer in (b"\r\n", b"\n"):
                        break
                break
            chunks.append(rfile.read(size))
            rfile.read(2)
        return b"".join(chunks) or b"{}"
    length = int(headers.get("Content-Length", "0") or "0")
    return rfile.read(length) if length else b"{}"


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
        path = urlparse(self.path).path
        if path not in {"/v1/jobs/clean", "/v1/jobs/analyze"}:
            self._send(404, {"code": "NOT_FOUND", "message": "not found"})
            return
        raw = read_http_body(self.headers, self.rfile)
        try:
            data = json.loads(raw.decode("utf-8") or "{}")
        except json.JSONDecodeError:
            self._send(400, {"code": "BAD_JSON", "message": "请求体不是 JSON"})
            return
        reviews = data.get("reviews") or []
        if not isinstance(reviews, list):
            self._send(400, {"code": "BAD_REVIEWS", "message": "reviews 必须是数组"})
            return
        aspects = data.get("aspects") or []
        if not isinstance(aspects, list):
            aspects = []
        if path == "/v1/jobs/clean":
            sys.stderr.write("clean reviews=%s\n" % len(reviews))
            result = clean_reviews(reviews, aspects)
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
            return
        llm = data.get("llm") if isinstance(data.get("llm"), dict) else None
        sys.stderr.write("analyze reviews=%s llm=%s\n" % (len(reviews), bool(llm and llm.get("apiKey"))))
        result = analyze_reviews(reviews, aspects, llm)
        self._send(
            200,
            {
                "code": "0",
                "message": result["message"],
                "jobId": data.get("jobId"),
                "projectId": data.get("projectId"),
                "analyzed": result["analyzed"],
                "aspectCount": result["aspectCount"],
                "source": result["source"],
                "items": result["items"],
            },
        )


def main() -> None:
    httpd = ThreadingHTTPServer((HOST, PORT), WorkerHandler)
    print("lumiinsight-ai-worker listening on http://%s:%s" % (HOST, PORT), flush=True)
    httpd.serve_forever()


if __name__ == "__main__":
    main()
