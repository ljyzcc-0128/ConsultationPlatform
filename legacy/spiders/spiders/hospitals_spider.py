import scrapy
from scrapy.spiders import CrawlSpider, Rule
from scrapy.linkextractors import LinkExtractor
from ..items import HospitalItem
from datetime import datetime

class HospitalsSpider(scrapy.Spider):
    name = 'hospitals'
    allowed_domains = ['example.com']  # 替换为实际的医院网站域名
    start_urls = ['https://example.com/hospitals']  # 替换为实际的URL

    def parse(self, response):
        """解析医院列表页面"""
        # 查找所有医院链接
        hospital_links = response.css('.hospital-item a::attr(href)').getall()

        for link in hospital_links:
            # 构建绝对URL
            absolute_url = response.urljoin(link)
            yield scrapy.Request(absolute_url, callback=self.parse_hospital)

    def parse_hospital(self, response):
        """解析医院详情页面"""
        self.logger.info(f'正在爬取医院: {response.url}')

        hospital = HospitalItem()

        # 提取医院信息
        hospital['name'] = response.css('.hospital-name::text').get('').strip()
        hospital['level'] = response.css('.hospital-level::text').get('').strip()
        hospital['address'] = response.css('.address::text').get('').strip()
        hospital['phone'] = response.css('.phone::text').get('').strip()
        hospital['website'] = response.css('.website::attr(href)').get('')
        hospital['description'] = response.css('.description::text').get('').strip()
        hospital['rating'] = self._get_rating(response)
        hospital['review_count'] = self._get_review_count(response)
        hospital['latitude'] = self._get_coordinates(response, 'latitude')
        hospital['longitude'] = self._get_coordinates(response, 'longitude')
        hospital['source_url'] = response.url
        hospital['crawl_time'] = datetime.now()

        yield hospital

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

    def _get_coordinates(self, response, coord_type):
        """获取经纬度坐标"""
        # 示例选择器
        coord_map = {
            'latitude': 'data-lat',
            'longitude': 'data-lng'
        }

        if coord_type in coord_map:
            coord_value = response.css(f'[data-coordinates]::{coord_map[coord_type]}').get('')
            try:
                return float(coord_value)
            except ValueError:
                return None
        return None