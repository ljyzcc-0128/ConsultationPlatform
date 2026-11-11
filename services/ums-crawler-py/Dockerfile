FROM mcr.microsoft.com/playwright/python:v1.40.0-jammy
WORKDIR /app
COPY requirements.txt .
RUN pip install -i https://pypi.tuna.tsinghua.edu.cn/simple --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8100
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8100"]