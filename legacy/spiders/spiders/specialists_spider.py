import scrapy
from ..items import SpecialistItem
from datetime import datetime
import re

class SpecialistsSpider(scrapy.Spider):
    name = 'specialists'
    allowed_domains = ['example.com']  # 替换为实际的医院网站域名
    start_urls = ['https://example.com/specialists']  # 替换为实际的URL

    def parse(self, response):
        """解析专家号源页面"""
        self.logger.info(f'正在爬取专家号源: {response.url}')

        # 获取所有专家
        specialists = response.css('.specialist-item')

        for specialist in specialists:
            specialist_data = SpecialistItem()

            # 提取医生信息
            specialist_data['doctor_name'] = specialist.css('.doctor-name::text').get('').strip()
            specialist_data['title'] = specialist.css('.title::text').get('').strip()
            specialist_data['department'] = specialist.css('.department::text').get('').strip()
            specialist_data['hospital'] = specialist.css('.hospital-name::text').get('').strip()

            # 解析出诊日期和时间
            schedule_text = specialist.css('.schedule::text').get('').strip()
            appointment_date, appointment_time = self._parse_schedule(schedule_text)
            specialist_data['appointment_date'] = appointment_date
            specialist_data['appointment_time'] = appointment_time

            # 获取挂号类型
            registration_type = specialist.css('.registration-type::text').get('').strip()
            specialist_data['registration_type'] = self._normalize_registration_type(registration_type)

            # 获取可预约数量
            available_count = specialist.css('.available-count::text').get('0')
            specialist_data['available_count'] = int(available_count) if available_count.isdigit() else 0

            # 获取挂号费
            fee_text = specialist.css('.fee::text').get('')
            specialist_data['registration_fee'] = self._extract_fee(fee_text)

            specialist_data['source_url'] = response.url
            specialist_data['crawl_time'] = datetime.now()

            yield specialist_data

    def _parse_schedule(self, schedule_str):
        """解析出诊时间字符串"""
        if not schedule_str:
            return None, None

        # 示例格式: "1月20日 周六 上午"
        try:
            # 尝试匹配常见格式
            patterns = [
                # 1月20日 周六 上午
                r'(\d{1,2})月(\d{1,2})日.*?(\d{1,2}):(\d{2})',
                # 2024-01-20 周六 14:30
                r'(\d{4})-(\d{2})-(\d{2})',
                # 2024/01/20 14:30
                r'(\d{4})/(\d{2})/(\d{2})\s+(\d{2}):(\d{2})'
            ]

            for pattern in patterns:
                match = re.search(pattern, schedule_str)
                if match:
                    if len(match.groups()) == 5:
                        # 年月日时间格式
                        year, month, day, hour, minute = match.groups()
                        date_str = f"{year}-{month.zfill(2)}-{day.zfill(2)}"
                        time_str = f"{hour.zfill(2)}:{minute.zfill(2)}"
                        return date_str, time_str
                    elif len(match.groups()) == 4:
                        # 月日时间格式
                        month, day, hour, minute = match.groups()
                        # 假设是当前年份
                        from datetime import datetime
                        current_year = datetime.now().year
                        date_str = f"{current_year}-{month.zfill(2)}-{day.zfill(2)}"
                        time_str = f"{hour.zfill(2)}:{minute.zfill(2)}"
                        return date_str, time_str
                    elif len(match.groups()) == 3:
                        # 只有年月日
                        year, month, day = match.groups()
                        date_str = f"{year}-{month.zfill(2)}-{day.zfill(2)}"
                        return date_str, "00:00"

        except Exception as e:
            self.logger.error(f'解析出诊时间失败: {schedule_str}, 错误: {e}')

        return None, None

    def _normalize_registration_type(self, type_str):
        """标准化挂号类型"""
        type_mapping = {
            '普通': '普通',
            '专家': '专家',
            '特需': '特需',
            '国际': '国际',
            '急诊': '急诊',
            '预约': '预约'
        }

        normalized = type_str.lower().strip()
        for key, value in type_mapping.items():
            if key in normalized:
                return value

        return '其他'

    def _extract_fee(self, fee_str):
        """从字符串中提取金额"""
        if not fee_str:
            return 0.0

        # 使用正则表达式提取数字
        match = re.search(r'(\d+\.?\d*)', fee_str)
        if match:
            return float(match.group(1))

        return 0.0