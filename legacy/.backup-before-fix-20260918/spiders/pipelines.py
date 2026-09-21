import json
import pymysql
import os
from datetime import datetime
from itemadapter import ItemAdapter
from config import config

class SaveToDatabasePipeline:
    """保存数据到MySQL数据库"""

    def __init__(self):
        self.conn = None
        self.cursor = None
        self.connect_to_db()

    def connect_to_db(self):
        """连接到数据库"""
        db_config = {
            'host': 'localhost',
            'user': 'root',
            'password': 'root1234',
            'db': 'consultation_platform',
            'charset': 'utf8mb4',
            'cursorclass': pymysql.cursors.DictCursor
        }

        try:
            self.conn = pymysql.connect(**db_config)
            self.cursor = self.conn.cursor()
            print("数据库连接成功")
        except Exception as e:
            print(f"数据库连接失败: {e}")

    def process_item(self, item, spider):
        """处理爬虫 item"""
        try:
            # 根据item类型保存到不同的表
            if isinstance(item, dict):
                item_type = item.get('type', 'unknown')

                if item_type == 'doctor':
                    self.save_doctor(item)
                elif item_type == 'hospital':
                    self.save_hospital(item)
                elif item_type == 'appointment':
                    self.save_appointment(item)
                elif item_type == 'specialist':
                    self.save_specialist(item)
            else:
                spider.logger.warning(f"Unknown item type: {type(item)}")

        except Exception as e:
            spider.logger.error(f"保存数据时出错: {e}")

        return item

    def save_doctor(self, doctor_data):
        """保存医生数据"""
        sql = """
        INSERT INTO doctors (
            name, title, department, hospital, specialty, description,
            photo_url, consultation_fee, experience_years, education,
            licenses, rating, review_count, source_url, crawl_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
        title=VALUES(title), department=VALUES(department), hospital=VALUES(hospital),
        specialty=VALUES(specialty), description=VALUES(description),
        photo_url=VALUES(photo_url), consultation_fee=VALUES(consultation_fee),
        experience_years=VALUES(experience_years), education=VALUES(education),
        licenses=VALUES(licenses), rating=VALUES(rating), review_count=VALUES(review_count),
        source_url=VALUES(source_url), crawl_time=VALUES(crawl_time)
        """

        data = (
            doctor_data.get('name'), doctor_data.get('title'), doctor_data.get('department'),
            doctor_data.get('hospital'), doctor_data.get('specialty'), doctor_data.get('description'),
            doctor_data.get('photo_url'), doctor_data.get('consultation_fee'),
            doctor_data.get('experience_years'), doctor_data.get('education'),
            doctor_data.get('licenses'), doctor_data.get('rating', 0.0),
            doctor_data.get('review_count', 0), doctor_data.get('source_url'),
            datetime.now()
        )

        self.cursor.execute(sql, data)
        self.conn.commit()

    def save_hospital(self, hospital_data):
        """保存医院数据"""
        sql = """
        INSERT INTO hospitals (
            name, level, address, phone, website, description,
            rating, review_count, latitude, longitude, source_url, crawl_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
        level=VALUES(level), address=VALUES(address), phone=VALUES(phone),
        website=VALUES(website), description=VALUES(description), rating=VALUES(rating),
        review_count=VALUES(review_count), latitude=VALUES(latitude),
        longitude=VALUES(longitude), source_url=VALUES(source_url), crawl_time=VALUES(crawl_time)
        """

        data = (
            hospital_data.get('name'), hospital_data.get('level'),
            hospital_data.get('address'), hospital_data.get('phone'),
            hospital_data.get('website'), hospital_data.get('description'),
            hospital_data.get('rating', 0.0), hospital_data.get('review_count', 0),
            hospital_data.get('latitude'), hospital_data.get('longitude'),
            hospital_data.get('source_url'), datetime.now()
        )

        self.cursor.execute(sql, data)
        self.conn.commit()

    def save_appointment(self, appointment_data):
        """保存预约数据"""
        # 这里可以根据需要实现预约数据的保存逻辑
        pass

    def save_specialist(self, specialist_data):
        """保存专家号源数据"""
        # 这里可以根据需要实现专家号源数据的保存逻辑
        pass

    def close_spider(self, spider):
        """关闭爬虫时关闭数据库连接"""
        if self.conn:
            self.conn.close()
            print("数据库连接已关闭")

class CleanDataPipeline:
    """数据清洗管道"""

    def process_item(self, item, spider):
        """清洗数据"""
        if isinstance(item, dict):
            # 清理字符串字段
            for key, value in item.items():
                if isinstance(value, str):
                    # 去除首尾空格
                    value = value.strip()
                    # 空字符串设为None
                    if not value:
                        item[key] = None

            # 确保数值字段存在
            numeric_fields = ['rating', 'review_count', 'consultation_fee', 'experience_years']
            for field in numeric_fields:
                if field in item and item[field] is not None:
                    try:
                        item[field] = float(item[field])
                    except (ValueError, TypeError):
                        item[field] = 0.0

        return item

class SaveToFilePipeline:
    """保存数据到文件"""

    def __init__(self):
        self.output_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'data', 'raw')
        os.makedirs(self.output_dir, exist_ok=True)

    def process_item(self, item, spider):
        """保存数据到JSON文件"""
        if isinstance(item, dict):
            # 添加类型标识
            item_type = 'unknown'
            if 'name' in item and 'department' in item:
                item_type = 'doctor'
            elif 'name' in item and 'level' in item:
                item_type = 'hospital'

            item['type'] = item_type
            item['save_time'] = datetime.now().isoformat()

            # 保存到文件
            filename = f"{item_type}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
            filepath = os.path.join(self.output_dir, filename)

            with open(filepath, 'w', encoding='utf-8') as f:
                json.dump(item, f, ensure_ascii=False, indent=2)

            spider.logger.info(f"数据已保存到: {filepath}")

        return item