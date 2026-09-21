import json
import pymysql
import os
from datetime import datetime
from itemadapter import ItemAdapter
from spiders.items import DoctorItem, HospitalItem, AppointmentItem, SpecialistItem


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
        """处理爬虫 item，按 Item 类型分发到对应的保存方法"""
        try:
            # scrapy.Item 不是 dict，必须用 ItemAdapter 统一读取字段
            data = ItemAdapter(item).asdict()

            if isinstance(item, DoctorItem):
                self.save_doctor(data)
            elif isinstance(item, HospitalItem):
                self.save_hospital(data)
            elif isinstance(item, AppointmentItem):
                self.save_appointment(data)
            elif isinstance(item, SpecialistItem):
                self.save_specialist(data)
            else:
                spider.logger.warning(f"Unknown item type: {type(item)}")

        except Exception as e:
            # 记录完整堆栈，避免数据静默丢失
            spider.logger.error(f"保存数据时出错: {e}", exc_info=True)

        return item

    def _resolve_hospital(self, hospital_name, hospital_level=None):
        """把医院名称解析成 hospitals.id；不存在则先插入一条最小记录"""
        if not hospital_name:
            return None

        self.cursor.execute("SELECT id FROM hospitals WHERE name = %s", (hospital_name,))
        row = self.cursor.fetchone()
        if row:
            return row['id']

        self.cursor.execute(
            "INSERT INTO hospitals (name, level, rating, review_count) VALUES (%s, %s, 0, 0)",
            (hospital_name, hospital_level)
        )
        self.conn.commit()
        return self.cursor.lastrowid

    def save_doctor(self, doctor_data):
        """保存医生数据"""
        # 爬虫抓到的是医院名称，模型用的是 hospital_id 外键
        hospital_id = self._resolve_hospital(
            doctor_data.get('hospital'),
            doctor_data.get('hospital_level')
        )

        crawl_time = doctor_data.get('crawl_time')
        if isinstance(crawl_time, str):
            try:
                crawl_time = datetime.fromisoformat(crawl_time)
            except ValueError:
                crawl_time = None

        sql = """
        INSERT INTO doctors (
            name, title, department, hospital_id, specialty, description,
            photo_url, consultation_fee, experience_years, education,
            licenses, rating, review_count, source_url, crawl_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            title=VALUES(title), department=VALUES(department), hospital_id=VALUES(hospital_id),
            specialty=VALUES(specialty), description=VALUES(description),
            photo_url=VALUES(photo_url), consultation_fee=VALUES(consultation_fee),
            experience_years=VALUES(experience_years), education=VALUES(education),
            licenses=VALUES(licenses), rating=VALUES(rating), review_count=VALUES(review_count),
            crawl_time=VALUES(crawl_time)
        """

        data = (
            doctor_data.get('name'), doctor_data.get('title'), doctor_data.get('department'),
            hospital_id, doctor_data.get('specialty'), doctor_data.get('description'),
            doctor_data.get('photo_url'), doctor_data.get('consultation_fee'),
            doctor_data.get('experience_years'), doctor_data.get('education'),
            doctor_data.get('licenses'), doctor_data.get('rating', 0.0),
            doctor_data.get('review_count', 0), doctor_data.get('source_url'),
            crawl_time
        )

        self.cursor.execute(sql, data)
        self.conn.commit()

    def save_hospital(self, hospital_data):
        """保存医院数据"""
        crawl_time = hospital_data.get('crawl_time')
        if isinstance(crawl_time, str):
            try:
                crawl_time = datetime.fromisoformat(crawl_time)
            except ValueError:
                crawl_time = None

        sql = """
        INSERT INTO hospitals (
            name, level, address, phone, website, description,
            rating, review_count, latitude, longitude, source_url, crawl_time
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            level=VALUES(level), address=VALUES(address), phone=VALUES(phone),
            website=VALUES(website), description=VALUES(description), rating=VALUES(rating),
            review_count=VALUES(review_count), latitude=VALUES(latitude),
            longitude=VALUES(longitude), crawl_time=VALUES(crawl_time)
        """

        data = (
            hospital_data.get('name'), hospital_data.get('level'),
            hospital_data.get('address'), hospital_data.get('phone'),
            hospital_data.get('website'), hospital_data.get('description'),
            hospital_data.get('rating', 0.0), hospital_data.get('review_count', 0),
            hospital_data.get('latitude'), hospital_data.get('longitude'),
            hospital_data.get('source_url'), crawl_time
        )

        self.cursor.execute(sql, data)
        self.conn.commit()

    def save_appointment(self, appointment_data):
        """保存预约数据"""
        # TODO: 根据需要实现预约数据的保存逻辑
        pass

    def save_specialist(self, specialist_data):
        """保存专家号源数据"""
        # TODO: 根据需要实现专家号源数据的保存逻辑
        pass

    def close_spider(self, spider):
        """关闭爬虫时关闭数据库连接"""
        if self.conn:
            self.conn.close()
            print("数据库连接已关闭")


class CleanDataPipeline:
    """数据清洗管道"""

    def process_item(self, item, spider):
        """清洗数据（原地修改，保持 item 类型不变，供后续管道使用）"""
        adapter = ItemAdapter(item)

        for key in list(adapter.keys()):
            value = adapter[key]
            if isinstance(value, str):
                value = value.strip()
                # 空字符串设为None
                adapter[key] = value if value else None

        # 确保数值字段存在且为数值
        numeric_fields = ['rating', 'review_count', 'consultation_fee', 'experience_years']
        for field in numeric_fields:
            if field in adapter and adapter[field] is not None:
                try:
                    adapter[field] = float(adapter[field])
                except (ValueError, TypeError):
                    adapter[field] = 0.0

        return item


class SaveToFilePipeline:
    """保存数据到文件"""

    def __init__(self):
        self.output_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'data', 'raw')
        os.makedirs(self.output_dir, exist_ok=True)

    def process_item(self, item, spider):
        """保存数据到JSON文件"""
        # scrapy.Item 不允许给未声明的字段赋值，先转成普通 dict
        data = ItemAdapter(item).asdict()

        if isinstance(item, DoctorItem):
            item_type = 'doctor'
        elif isinstance(item, HospitalItem):
            item_type = 'hospital'
        elif isinstance(item, AppointmentItem):
            item_type = 'appointment'
        elif isinstance(item, SpecialistItem):
            item_type = 'specialist'
        else:
            item_type = 'unknown'

        data['type'] = item_type
        data['save_time'] = datetime.now().isoformat()

        # 按天合并写入 jsonl，避免每条数据各生成一个文件
        filename = f"{item_type}_{datetime.now().strftime('%Y%m%d')}.jsonl"
        filepath = os.path.join(self.output_dir, filename)

        with open(filepath, 'a', encoding='utf-8') as f:
            f.write(json.dumps(data, ensure_ascii=False, default=str) + '\n')

        spider.logger.info(f"数据已保存到: {filepath}")

        return item
