# 咨询平台 (Consultation Platform)

一个用于爬取和管理医院、医生、预约信息的综合性平台。

## 🚀 功能特性

### 🔍 爬虫功能
- **医生信息爬取**：爬取医生的姓名、职称、科室、专业特长、简介等信息
- **医院信息爬取**：爬取医院的名称、等级、地址、联系方式等信息
- **预约信息爬取**：爬取门诊预约信息，包括可预约数量、挂号费等
- **专家号源监控**：实时监控专家号源情况，支持多种挂号类型

### 🏥 数据管理
- **医生管理**：完整的医生信息CRUD操作
- **医院管理**：医院信息管理和分类
- **预约管理**：预约系统，支持时间验证和状态管理
- **评分系统**：支持医生和医院的评分功能

### 📊 数据分析
- **统计分析**：生成医生、医院分布统计
- **可视化图表**：使用matplotlib和seaborn生成各类图表
- **报告生成**：自动生成综合分析报告

### 🛠️ 技术栈
- **后端框架**：Flask + SQLAlchemy
- **爬虫框架**：Scrapy
- **数据库**：MySQL
- **缓存**：Redis
- **任务队列**：Celery
- **数据分析**：Pandas + Matplotlib + Seaborn

## 📁 项目结构

```
ConsultationPlatform/
├── app/                    # 主应用目录
│   ├── models/            # 数据模型
│   │   └── __init__.py    # 模型定义
│   ├── services/          # 业务逻辑
│   │   ├── doctor_service.py
│   │   └── hospital_service.py
│   ├── api/               # API接口
│   │   ├── doctor_routes.py
│   │   ├── hospital_routes.py
│   │   ├── appointment_routes.py
│   │   └── crawler_routes.py
│   └── utils/             # 工具函数
│       └── helpers.py
├── spiders/               # 爬虫目录
│   ├── spiders/           # 具体爬虫
│   │   ├── doctors_spider.py
│   │   ├── hospitals_spider.py
│   │   ├── appointments_spider.py
│   │   └── specialists_spider.py
│   ├── items.py           # 数据项定义
│   ├── pipelines.py       # 数据处理管道
│   └── settings.py        # Scrapy配置
├── data_processing/       # 数据处理
│   ├── clean_doctors.py   # 医生数据清洗
│   └── clean_hospitals.py # 医院数据清洗
├── analysis/             # 数据分析
│   └── generate_reports.py # 报告生成
├── tests/               # 测试目录
│   ├── test_doctor_service.py
│   └── test_helpers.py
├── .vscode/             # VS Code配置
│   ├── settings.json    # VS Code设置
│   └── launch.json      # 调试配置
├── config.py            # 项目配置
├── main.py              # 主程序入口
├── requirements.txt     # Python依赖
├── Dockerfile          # Docker构建文件
├── docker-compose.yml  # Docker编排
├── Makefile            # 构建脚本
└── README.md            # 项目说明
```

## 🛠️ 安装说明

### 1. 环境要求
- Python 3.8+
- MySQL 5.7+
- Redis 6.0+

### 2. 快速开始

#### 使用Makefile（推荐）
```bash
# 克隆项目
git clone <repository-url>
cd ConsultationPlatform

# 一键设置开发环境
make setup-dev
```

#### 手动安装
```bash
# 创建虚拟环境
python -m venv venv
source venv/bin/activate  # Linux/Mac

# 安装依赖
pip install -r requirements.txt

# 配置环境变量
cp env.example .env
# 编辑.env文件设置数据库等信息

# 初始化数据库
make migrate-db

# 启动服务
make dev
```

### 3. Docker部署
```bash
# 构建并启动所有服务
make docker-up

# 查看日志
make docker-logs

# 停止服务
make docker-down
```

## 📖 使用说明

### Web服务启动
```bash
# 开发模式
make dev

# 生产模式
python main.py
```

### API接口示例

#### 获取医生列表
```bash
curl "http://localhost:5000/api/doctors?page=1&per_page=10"
```

#### 创建医生
```bash
curl -X POST "http://localhost:5000/api/doctors" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "张医生",
    "title": "主任医师",
    "department": "内科",
    "hospital_id": 1,
    "specialty": "心血管疾病"
  }'
```

#### 运行爬虫
```bash
# 运行医生爬虫
scrapy crawl doctors

# 运行所有爬虫
make run-crawler-all
```

### 数据分析

#### 清洗数据
```bash
# 清洗医生数据
python data_processing/clean_doctors.py

# 清洗医院数据
python data_processing/clean_hospitals.py
```

#### 生成报告
```bash
# 生成综合分析报告
python analysis/generate_reports.py
```

## 📊 数据库设计

### 医生表 (doctors)
```sql
CREATE TABLE doctors (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  title VARCHAR(50),
  department VARCHAR(100) NOT NULL,
  hospital_id INT,
  specialty TEXT,
  description TEXT,
  photo_url VARCHAR(255),
  rating FLOAT DEFAULT 0.0,
  review_count INT DEFAULT 0,
  consultation_fee FLOAT,
  experience_years INT,
  education VARCHAR(255),
  licenses TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
);
```

### 医院表 (hospitals)
```sql
CREATE TABLE hospitals (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(200) NOT NULL,
  level VARCHAR(50),
  address VARCHAR(500),
  phone VARCHAR(20),
  website VARCHAR(255),
  description TEXT,
  rating FLOAT DEFAULT 0.0,
  review_count INT DEFAULT 0,
  latitude FLOAT,
  longitude FLOAT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 预约表 (appointments)
```sql
CREATE TABLE appointments (
  id INT PRIMARY KEY AUTO_INCREMENT,
  doctor_id INT NOT NULL,
  patient_name VARCHAR(100) NOT NULL,
  patient_phone VARCHAR(20) NOT NULL,
  appointment_date DATE NOT NULL,
  appointment_time TIME NOT NULL,
  status VARCHAR(20) DEFAULT 'pending',
  notes TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (doctor_id) REFERENCES doctors(id)
);
```

## 🔧 开发指南

### 添加新的爬虫
1. 在 `spiders/spiders/` 目录创建新的爬虫文件
2. 继承 `scrapy.Spider` 类
3. 实现 `parse` 方法
4. 在 `settings.py` 中配置管道

### 添加新的API接口
1. 在 `app/api/` 目录创建新的路由文件
2. 创建服务类处理业务逻辑
3. 在 `main.py` 中注册蓝图

### 数据处理流程
```
原始数据 → 数据清洗 → 数据验证 → 存储到数据库 → 业务处理 → API返回
```

## 📋 常用命令

```bash
# 开发相关
make dev              # 启动开发服务器
make test             # 运行测试
make lint             # 代码检查
make format           # 代码格式化

# 数据相关
make migrate-db       # 数据库迁移
make reset-db        # 重置数据库
make run-crawler      # 运行爬虫
make clean-data      # 清理数据

# Docker相关
make docker-up       # 启动Docker
make docker-down     # 停止Docker
make docker-logs     # 查看日志

# 部署相关
make deploy          # 部署到生产环境
```

## ⚠️ 注意事项

1. **爬虫道德**：请遵守网站的robots.txt协议，合理设置爬取间隔
2. **数据隐私**：妥善处理用户数据，遵守相关法律法规
3. **性能优化**：大数据量时考虑分页和缓存
4. **错误处理**：完善异常处理机制，保证系统稳定性

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 📞 联系方式

- 项目地址：[GitHub Repository](https://github.com/your-username/consultation-platform)
- 邮箱：your-email@example.com