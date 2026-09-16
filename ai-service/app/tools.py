"""Backend tools — ALWAYS called with the END USER's JWT so the core API
re-verifies authorization. The LLM (and this service) is never the security
boundary; a tool result of 403 becomes a refusal, never data leakage.

SSRF guard (defense in depth, per Mimosa guidance):
- scheme restricted to http
- host restricted to the compose-internal allowlist {"backend"}
- redirects disabled, connect+read timeouts enforced
- no user/model input flows into the URL (paths are module constants)
"""
import json

import requests

ALLOWED_HOSTS = frozenset({"backend"})
SCHEDULE_PATH = "/api/v1/schedule/sessions"
GRADES_PATH = "/api/v1/grades/me"
TIMEOUT = (3, 10)


def _guard(url: str) -> None:
    if not url.startswith("http://backend:8080/"):
        raise ValueError(f"blocked non-allowlisted tool URL: {url[:60]}")


def _get(url: str, user_jwt: str) -> tuple[int, dict]:
    _guard(url)
    resp = requests.get(
        url,
        headers={"Authorization": "Bearer " + user_jwt},
        timeout=TIMEOUT,
        allow_redirects=False,
    )
    try:
        return resp.status_code, resp.json()
    except ValueError:
        return resp.status_code, {}


def get_my_schedule(user_jwt: str, days: int = 7) -> dict:
    from datetime import date, timedelta
    days = max(1, min(days, 31))
    start, end = date.today(), date.today() + timedelta(days=days)
    url = f"http://backend:8080{SCHEDULE_PATH}?from={start}&to={end}"
    status, body = _get(url, user_jwt)
    if status != 200:
        return {"error": status, "message": body.get("error", {}).get("message", "backend error")}
    sessions = body.get("data") or []
    return {"count": len(sessions), "sessions": [
        {"date": s["date"], "time": f"{s['startAt'][11:16]}-{s['endAt'][11:16]}",
         "course": f"{s['courseCode']} {s['courseName']}", "room": f"{s['building']}/{s['room']}"}
        for s in sessions
    ]}


def get_my_grades(user_jwt: str) -> dict:
    status, body = _get("http://backend:8080" + GRADES_PATH, user_jwt)
    if status != 200:
        # e.g. another student's grades requested -> backend 403 -> refusal
        return {"error": status, "message": body.get("error", {}).get("message", "backend error")}
    return {"courses": body.get("data") or []}


def search_regulations(query: str, enrolled_course_codes: list[str]) -> list[dict]:
    from . import rag
    return rag.retrieve(query, enrolled_course_codes)
