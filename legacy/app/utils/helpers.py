import re
from datetime import datetime, timedelta
import hashlib

def validate_phone(phone):
    """验证手机号格式"""
    pattern = r'^1[3-9]\d{9}$'
    return re.match(pattern, phone) is not None

def validate_email(email):
    """验证邮箱格式"""
    pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
    return re.match(pattern, email) is not None

def format_date(date_str):
    """格式化日期字符串，返回 date 对象（与 Appointment.appointment_date 列类型一致）"""
    try:
        date_obj = datetime.strptime(date_str, '%Y-%m-%d')
        return date_obj.date()
    except ValueError:
        return None

def format_time(time_str):
    """格式化时间字符串"""
    try:
        time_obj = datetime.strptime(time_str, '%H:%M').time()
        return time_obj
    except ValueError:
        return None

def generate_md5(text):
    """生成MD5哈希"""
    return hashlib.md5(text.encode()).hexdigest()

def slugify(text):
    """生成URL友好的slug"""
    text = text.lower()
    text = re.sub(r'[^a-z0-9\s-]', '', text)
    text = re.sub(r'[\s-]+', '-', text)
    return text.strip('-')

def is_weekend(date):
    """判断是否是周末"""
    return date.weekday() >= 5

def calculate_working_hours(start_time, end_time):
    """计算工作时长（小时）"""
    if start_time and end_time:
        delta = end_time - start_time
        return delta.total_seconds() / 3600
    return 0

def sanitize_html(html_content):
    """清理HTML内容，防止XSS"""
    # 移除危险的标签
    dangerous_tags = ['script', 'iframe', 'object', 'embed']
    for tag in dangerous_tags:
        html_content = re.sub(f'<{tag}[^>]*>.*?</{tag}>', '', html_content, flags=re.IGNORECASE | re.DOTALL)
    # 移除onclick等事件属性
    html_content = re.sub(r'on\w+="[^"]*"', '', html_content, flags=re.IGNORECASE)
    return html_content