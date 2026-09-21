.PHONY: help install dev test clean run docker-build docker-up docker-down

help:  ## 显示帮助信息
	@echo "可用的命令:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

install:  ## 安装依赖
	pip install -r requirements-py313.txt

dev:  ## 开发环境运行
	python main.py

test:  ## 运行测试
	pytest tests/ -v

test-cov:  ## 运行测试并生成覆盖率报告
	pytest tests/ --cov=app --cov-report=html

clean:  ## 清理临时文件和缓存
	find . -type f -name "*.pyc" -delete
	find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
	find . -type d -name "*.egg-info" -exec rm -rf {} + 2>/dev/null || true
	rm -rf build/
	rm -rf dist/
	rm -rf .pytest_cache/
	rm -rf htmlcov/

run-crawler:  ## 运行爬虫
	scrapy crawl doctors

run-crawler-all:  ## 运行所有爬虫
	scrapy crawl doctors
	scrapy crawl hospitals
	scrapy crawl appointments
	scrapy crawl specialists

migrate-db:  ## 数据库建表
	python -c "from main import create_app; create_app()"

reset-db:  ## 重置数据库（清空所有表并重建，慎用）
	python -c "from main import create_app; from app.models import db; app = create_app(); ctx = app.app_context(); ctx.push(); db.drop_all(); db.create_all(); ctx.pop()"

docker-build:  ## 构建Docker镜像
	docker build -t consultation-platform .

docker-up:  ## 启动Docker服务
	docker-compose up -d

docker-down:  ## 停止Docker服务
	docker-compose down

docker-logs:  ## 查看Docker日志
	docker-compose logs -f

shell:  ## 进入Docker容器
	docker-compose exec web bash

setup-dev: install migrate-db  ## 设置开发环境
	@echo "开发环境设置完成!"

deploy: clean test docker-build  ## 部署到生产环境
	@echo "部署完成!"

lint:  ## 代码检查
	flake8 app/ spiders/
	black --check app/ spiders/

format:  ## 代码格式化
	black app/ spiders/
	isort app/ spiders/