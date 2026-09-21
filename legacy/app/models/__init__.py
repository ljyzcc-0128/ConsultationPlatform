# 模型包初始化文件
# 这里可以导入所有模型，方便在其他地方引用
from datetime import datetime, date, time

from app import Doctor, Hospital, Appointment, CrawlJob, db

# 为模型添加to_dict方法
def to_dict(self):
    """将模型对象转换为字典"""
    result = {}
    for column in self.__table__.columns:
        value = getattr(self, column.name)
        if isinstance(value, (datetime, date, time)):
            value = value.isoformat()
        result[column.name] = value
    return result

# 添加到所有模型
Doctor.to_dict = to_dict
Hospital.to_dict = to_dict
Appointment.to_dict = to_dict
CrawlJob.to_dict = to_dict