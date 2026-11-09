#!/usr/bin/env python3
"""CI 中的 AI 代码评审脚本（MR 流水线专用）。

流程：获取 MR diff → 调用 LLM（OpenAI 兼容协议）评审 → 结果以评论贴回 MR。
降级策略：
  - 未配置 GITLAB_REVIEW_TOKEN（api 权限）：回退 git diff 取变更，评审结果仅打印到 job 日志，不贴评论；
  - 未配置 LLM（UMS_LLM_*）：直接跳过；
  - 任何异常均不使流水线失败（job 层面另有 allow_failure 兜底）。

所需 CI 变量：
  自动注入：CI_API_V4_URL / CI_PROJECT_ID / CI_MERGE_REQUEST_IID / CI_MERGE_REQUEST_TARGET_BRANCH_NAME
  手动配置：GITLAB_REVIEW_TOKEN（api scope PAT，用于读 diff 和贴评论）
           UMS_LLM_BASE_URL / UMS_LLM_API_KEY / UMS_LLM_MODEL（LLM 接入）
"""
import json
import os
import subprocess
import sys
import urllib.parse
import urllib.request

# 单文件 diff 上限字符数（超出截断，避免撑爆上下文）
MAX_DIFF_CHARS = 60_000
# 每个文件 diff 保留上限
MAX_FILE_DIFF_CHARS = 12_000

SYSTEM_PROMPT = """你是资深代码评审员，用中文对 MR 变更做简明评审。关注点：
1) 正确性与边界条件；2) 安全：硬编码密钥/密码、SQL注入、越权；3) 项目规约：禁止写死环境相关配置/路由（必须走配置文件与环境变量）；
4) 异常处理与资源释放；5) 明显的性能问题。
输出要求：markdown，最多 5 条，每条以「- `文件名`: 问题描述 + 建议」格式；无问题则输出「✅ 未发现明显问题」。不要输出与评审无关的内容。"""


def log(msg):
    print(f"[ai-review] {msg}", flush=True)


def http_json(url, token=None, data=None, method="GET"):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["PRIVATE-TOKEN"] = token
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


def fetch_diff_api(api, project_id, mr_iid, token):
    """优先走 API 读 MR diff（完整可靠）。/diffs 在部分 GitLab 版本不存在，用 /changes。"""
    url = f"{api}/projects/{project_id}/merge_requests/{mr_iid}/changes?per_page=100"
    data = http_json(url, token=token)
    files = data.get("changes") or []
    parts = []
    for f in files:
        d = (f.get("diff") or "")[:MAX_FILE_DIFF_CHARS]
        parts.append(f"--- {f.get('old_path')} → {f.get('new_path')}\n{d}")
    return "\n\n".join(parts)


def fetch_diff_git(target_branch):
    """无 token 时回退：本地 git diff 对比目标分支。"""
    subprocess.run(["git", "fetch", "origin", target_branch], capture_output=True)
    r = subprocess.run(
        ["git", "diff", f"origin/{target_branch}...HEAD"],
        capture_output=True, text=True)
    return r.stdout


def truncate(diff):
    if len(diff) > MAX_DIFF_CHARS:
        log(f"diff 超长（{len(diff)} 字符），截断至 {MAX_DIFF_CHARS}")
        diff = diff[:MAX_DIFF_CHARS] + "\n... [TRUNCATED]"
    return diff


def llm_review(diff):
    base = os.environ["UMS_LLM_BASE_URL"].rstrip("/")
    key = os.environ["UMS_LLM_API_KEY"]
    model = os.environ["UMS_LLM_MODEL"]
    endpoint = base if base.endswith("/chat/completions") else base + "/chat/completions"
    payload = {
        "model": model,
        "temperature": 0.2,
        "max_tokens": 2048,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": f"请评审以下 MR 变更：\n\n{diff}"},
        ],
    }
    req = urllib.request.Request(
        endpoint, data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {key}"},
        method="POST")
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode())["choices"][0]["message"]["content"]


def post_mr_note(api, project_id, mr_iid, token, text):
    url = f"{api}/projects/{project_id}/merge_requests/{mr_iid}/notes"
    body = urllib.parse.urlencode({"body": text}).encode()
    req = urllib.request.Request(
        url, data=body,
        headers={"PRIVATE-TOKEN": token,
                 "Content-Type": "application/x-www-form-urlencoded"},
        method="POST")
    urllib.request.urlopen(req, timeout=30)


def main():
    api = os.environ.get("CI_API_V4_URL", "")
    project_id = os.environ.get("CI_PROJECT_ID", "")
    mr_iid = os.environ.get("CI_MERGE_REQUEST_IID", "")
    target = os.environ.get("CI_MERGE_REQUEST_TARGET_BRANCH_NAME", "main")
    gh_token = os.environ.get("GITLAB_REVIEW_TOKEN", "").strip()

    if not all([os.environ.get(k, "").strip() for k in
                ("UMS_LLM_BASE_URL", "UMS_LLM_API_KEY", "UMS_LLM_MODEL")]):
        log("LLM 未配置（UMS_LLM_BASE_URL/API_KEY/MODEL），跳过 AI 评审")
        return 0

    try:
        diff = (fetch_diff_api(api, project_id, mr_iid, gh_token)
                if gh_token and api and project_id and mr_iid
                else fetch_diff_git(target))
    except Exception as e:
        log(f"获取 diff 失败，跳过评审: {e}")
        return 0

    if not diff.strip():
        log("MR 无变更内容，跳过评审")
        return 0

    review = llm_review(truncate(diff))
    log("---- 评审结果 ----")
    print(review, flush=True)

    header = "### 🤖 AI 代码评审\n\n"
    if gh_token and api and project_id and mr_iid:
        try:
            post_mr_note(api, project_id, mr_iid, gh_token, header + review)
            log(f"评审结果已贴到 MR !{mr_iid}")
        except Exception as e:
            log(f"贴评论失败（结果仅保留在 job 日志）: {e}")
    else:
        log("未配置 GITLAB_REVIEW_TOKEN，评审结果仅打印在 job 日志")
    return 0


if __name__ == "__main__":
    sys.exit(main())
