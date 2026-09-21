"""一次性脚本：重放 ums.raw.item.fetched.dlq 死信消息到 exchange ums（routing key raw.item.fetched）。
用法：venv/bin/python /tmp/replay_dlq.py
"""
import base64
import json
import urllib.request

BASE = 'http://127.0.0.1:15672/api'
AUTH = 'Basic ' + base64.b64encode(b'ums_admin:1eAODIRLqpRazsxV').decode()
DLQ = '/queues/%2F/ums.raw.item.fetched.dlq'
PUBLISH = '/exchanges/%2F/ums/publish'


def call(method, path, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header('Authorization', AUTH)
    data = None
    if body is not None:
        req.add_header('Content-Type', 'application/json')
        data = json.dumps(body).encode()
    with urllib.request.urlopen(req, data) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


msgs = call('POST', DLQ + '/get', {
    'count': 50, 'ackmode': 'ack_requeue_false',
    'encoding': 'auto', 'truncate': 200000,
})
print(f'从 DLQ 取出 {len(msgs)} 条消息')

for m in msgs:
    payload = m['payload']
    if isinstance(payload, dict):  # encoding=auto 时 python 客户端可能已是 dict
        payload = json.dumps(payload, ensure_ascii=False)
    resp = call('POST', PUBLISH, {
        'properties': {'content_type': 'application/json', 'delivery_mode': 2},
        'routing_key': 'raw.item.fetched',
        'payload': payload,
        'payload_encoding': 'string',
    })
    print('重放一条 ->', resp)

print('完成')
