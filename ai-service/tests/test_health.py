from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_reports_service_and_mock_mode():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["service"] == "ai-service"
    assert body["mock_mode"] is True


def test_ready_ok():
    response = client.get("/ready")
    assert response.status_code == 200
    assert response.json()["status"] == "ready"
