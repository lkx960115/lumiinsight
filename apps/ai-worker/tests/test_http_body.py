import io
import unittest
from email.message import Message

from app.main import read_http_body


class ReadHttpBodyTest(unittest.TestCase):
    def test_content_length(self):
        headers = Message()
        headers["Content-Length"] = "7"
        raw = read_http_body(headers, io.BytesIO(b'{"a":1}extra'))
        self.assertEqual(b'{"a":1}', raw)

    def test_chunked_body(self):
        headers = Message()
        headers["Transfer-Encoding"] = "chunked"
        payload = b'{"reviews":[{"id":1}]}'
        chunked = b"%x\r\n%s\r\n0\r\n\r\n" % (len(payload), payload)
        raw = read_http_body(headers, io.BytesIO(chunked))
        self.assertEqual(payload, raw)


if __name__ == "__main__":
    unittest.main()
