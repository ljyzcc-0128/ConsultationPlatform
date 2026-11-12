FROM mcr.microsoft.com/playwright/python:v1.40.0-jammy
WORKDIR /app
COPY requirements.txt .
RUN pip install -i https://mirrors.ustc.edu.cn/pypi/simple --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8100
# FastAPI 实例定义在 api/trigger.py，main.py 仅是 python main.py 的启动壳
CMD ["uvicorn", "api.trigger:app", "--host", "0.0.0.0", "--port", "8100"]