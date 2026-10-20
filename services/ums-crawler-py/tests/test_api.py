"""触发接口测试：不触网，mock run_source"""
from fastapi.testclient import TestClient

import api.trigger as trigger_module


def make_client():
    return TestClient(trigger_module.app)


def test_healthz():
    resp = make_client().get('/healthz')
    assert resp.status_code == 200 and resp.json()['status'] == 'healthy'


def test_trigger_returns_summary(monkeypatch):
    async def fake_run(source_code, since=None, limit=None, publisher=None):
        return {'task_id': 't1', 'source_code': source_code, 'fetched': 2,
                'published': 2, 'failed': 0, 'errors': [], 'mode': 'stdout'}
    monkeypatch.setattr(trigger_module, 'run_source', fake_run)
    resp = make_client().post('/internal/trigger/CN-01', json={'limit': 5})
    assert resp.status_code == 200
    body = resp.json()
    assert body['source_code'] == 'CN-01' and body['fetched'] == 2


def test_trigger_unknown_source_404(monkeypatch):
    async def fake_run(source_code, since=None, limit=None, publisher=None):
        raise FileNotFoundError('未找到信源选择器配置')
    monkeypatch.setattr(trigger_module, 'run_source', fake_run)
    resp = make_client().post('/internal/trigger/XX-99')
    assert resp.status_code == 404
    assert resp.json()['detail']['code'] == 'SRC_4041'
