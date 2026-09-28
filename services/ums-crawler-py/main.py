"""ums-crawler 服务入口：python main.py 启动触发接口（手册第三节）"""
import uvicorn

from settings import HOST, PORT

if __name__ == '__main__':
    uvicorn.run('api.trigger:app', host=HOST, port=PORT, reload=False)
