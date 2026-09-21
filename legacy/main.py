from flask import Flask, request, jsonify
from flask_cors import CORS
from config import config
from app.models import db
import os

def create_app(config_name=None):
    app = Flask(__name__)

    # 加载配置
    if config_name is None:
        config_name = os.environ.get('FLASK_ENV', 'development')
    app.config.from_object(config[config_name])

    # 初始化扩展
    db.init_app(app)
    CORS(app)

    # 创建数据库表
    with app.app_context():
        db.create_all()

    # 错误处理
    @app.errorhandler(404)
    def not_found(error):
        return jsonify({'error': 'Not found'}), 404

    @app.errorhandler(500)
    def internal_error(error):
        return jsonify({'error': 'Internal server error'}), 500

    # 根路由
    @app.route('/')
    def index():
        return jsonify({
            'message': 'Welcome to Consultation Platform API',
            'version': '1.0.0'
        })

    # 健康检查
    @app.route('/health')
    def health_check():
        return jsonify({'status': 'healthy', 'message': 'Service is running'})

    return app

if __name__ == '__main__':
    app = create_app()
    app.run(host='0.0.0.0', port=5000, debug=True)