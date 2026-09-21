import scrapy
from scrapy.spiders import CrawlSpider, Rule
from scrapy.linkextractors import LinkExtractor
from ..items import DoctorItem
from datetime import datetime

class DoctorsSpider(scrapy.Spider):
    name = 'doctors'
    allowed_domains = ['example.com']  # 替换为实际的医院网站域名
    start_urls = ['https://example.com/doctors']  # 替换为实际的URL

    def parse(self, response):
        """解析医生列表页面"""
        # 查找所有医生链接
        doctor_links = response.css('.doctor-item a::attr(href)').getall()

        for link in doctor_links:
            # 构建绝对URL
            absolute_url = response.urljoin(link)
            yield scrapy.Request(absolute_url, callback=self.parse_doctor)

    def parse_doctor(self, response):
        """解析医生详情页面"""
        self.logger.info(f'正在爬取医生: {response.url}')

        doctor = DoctorItem()

        # 提取医生信息
        doctor['name'] = response.css('.doctor-name::text').get('').strip()
        doctor['title'] = response.css('.doctor-title::text').get('').strip()
        doctor['department'] = response.css('.department::text').get('').strip()
        doctor['hospital'] = response.css('.hospital-name::text').get('').strip()
        doctor['specialty'] = self._get_specialty(response)
        doctor['description'] = response.css('.doctor-description::text').get('').strip()
        doctor['photo_url'] = response.css('.doctor-photo img::attr(src)').get('')
        doctor['consultation_fee'] = self._get_fee(response)
        doctor['experience_years'] = self._get_experience(response)
        doctor['education'] = response.css('.education::text').get('').strip()
        doctor['licenses'] = response.css('.licenses::text').get('').strip()
        doctor['rating'] = self._get_rating(response)
        doctor['review_count'] = self._get_review_count(response)
        doctor['source_url'] = response.url
        doctor['crawl_time'] = datetime.now()

        yield doctor

    def _get_specialty(self, response):
        """获取专业特长"""
        # 示例选择器，需要根据实际网站结构调整
        specialty = response.css('.specialty-list li::text').getall()
        return '\n'.join(s.strip() for s in specialty) if specialty else ''

    def _get_fee(self, response):
        """获取诊费"""
        # 示例选择器
        fee_text = response.css('.consultation-fee::text').get('')
        if fee_text:
            # 提取数字部分
            import re
            match = re.search(r'(\d+\.?\d*)', fee_text)
            if match:
                return float(match.group(1))
        return 0.0

    def _get_experience(self, response):
        """获取从医年限"""
        # 示例选择器
        experience_text = response.css('.experience::text').get('')
        if experience_text:
            # 提取数字部分
            import re
            match = re.search(r'(\d+)', experience_text)
            if match:
                return int(match.group(1))
        return 0

    def _get_rating(self, response):
        """获取评分"""
        # 示例选择器
        rating_text = response.css('.rating-value::text').get('0')
        try:
            return float(rating_text)
        except ValueError:
            return 0.0

    def _get_review_count(self, response):
        """获取评价数量"""
        # 示例选择器
        count_text = response.css('.review-count::text').get('0')
        try:
            return int(count_text)
        except ValueError:
            return 0