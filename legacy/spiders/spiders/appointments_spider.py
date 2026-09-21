import scrapy
from ..items import AppointmentItem
from datetime import datetime
import re

class AppointmentsSpider(scrapy.Spider):
    name = 'appointments'
    allowed_domains = ['example.com']  # 替换为实际的预约网站域名
    start_urls = ['https://example.com/appointments']  # 替换为实际的URL

    def parse(self, response):
        """解析预约页面"""
        self.logger.info(f'正在爬取预约信息: {response.url}')

        # 获取所有可预约的医生
        doctors = response.css('.appointment-doctor')

        for doctor in doctors:
            appointment_data = AppointmentItem()

            # 提取医生信息
            appointment_data['doctor_name'] = doctor.css('.doctor-name::text').get('').strip()
            appointment_data['doctor_title'] = doctor.css('.doctor-title::text').get('').strip()
            appointment_data['department'] = doctor.css('.department::text').get('').strip()
            appointment_data['hospital'] = doctor.css('.hospital-name::text').get('').strip()

            # 解析预约日期和时间
            date_time_str = doctor.css('.appointment-time::text').get('').strip()
            appointment_date, appointment_time = self._parse_datetime(date_time_str)
            appointment_data['appointment_date'] = appointment_date
            appointment_data['appointment_time'] = appointment_time

            # 获取可预约数量
            available_count = doctor.css('.available-count::text').get('0')
            appointment_data['available_count'] = int(available_count) if available_count.isdigit() else 0

            # 获取挂号费
            fee_text = doctor.css('.registration-fee::text').get('')
            appointment_data['registration_fee'] = self._extract_fee(fee_text)

            appointment_data['source_url'] = response.url
            appointment_data['crawl_time'] = datetime.now()

            yield appointment_data

    def _parse_datetime(self, datetime_str):
        """解析日期时间字符串"""
        # 示例格式: "2024-01-20 14:30"
        if not datetime_str:
            return None, None

        try:
            # 使用正则表达式提取日期和时间部分
            match = re.match(r'(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})', datetime_str)
            if match:
                date_str = match.group(1)
                time_str = match.group(2)
                return date_str, time_str
        except Exception as e:
            self.logger.error(f'解析日期时间失败: {datetime_str}, 错误: {e}')

        return None, None

    def _extract_fee(self, fee_str):
        """从字符串中提取金额"""
        if not fee_str:
            return 0.0

        # 使用正则表达式提取数字
        match = re.search(r'(\d+\.?\d*)', fee_str)
        if match:
            return float(match.group(1))

        return 0.0