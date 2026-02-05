#!/usr/bin/env python3

from datetime import datetime
from flask import Flask, jsonify, request

app = Flask(__name__)


@app.get("/health")
def health():
    return jsonify({"status": "ok", "timestamp": datetime.utcnow().isoformat() + "Z"})


@app.get("/test/api/hello")
@app.get("/api/hello")
def hello():
    return jsonify({
        "message": "Hello from test server",
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "seq": request.args.get("seq")
    })


@app.route("/test/api/echo", methods=["GET", "POST", "PUT", "PATCH"])
@app.route("/api/echo", methods=["GET", "POST", "PUT", "PATCH"])
def echo():
    return jsonify({
        "method": request.method,
        "args": request.args.to_dict(flat=True),
        "json": request.get_json(silent=True),
        "headers": {
            "Content-Type": request.headers.get("Content-Type"),
            "X-Form-Token": request.headers.get("X-Form-Token"),
            "X-Form-Load-Time": request.headers.get("X-Form-Load-Time"),
            "X-Honeypot": request.headers.get("X-Honeypot"),
            "X-Idempotency-Key": request.headers.get("X-Idempotency-Key")
        }
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=9000, debug=False)
