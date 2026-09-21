import scrapy

class DoctorItem(scrapy.Item):
    # 医生基本信息
    name = scrapy.Field()  # 医生姓名
    title = scrapy.Field()  # 职称
    department = scrapy.Field()  # 科室
    hospital = scrapy.Field()  # 所属医院
    hospital_level = scrapy.Field()  # 医院等级
    specialty = scrapy.Field()  # 专业特长
    description = scrapy.Field()  # 简介
    photo_url = scrapy.Field()  # 照片URL
    consultation_fee = scrapy.Field()  # 诊费
    experience_years = scrapy.Field()  # 从医年限
    education = scrapy.Field()  # 教育背景
    licenses = scrapy.Field()  # 执业证书
    rating = scrapy.Field()  # 评分
    review_count = scrapy.Field()  # 评价数量
    source_url = scrapy.Field()  # 来源URL
    crawl_time = scrapy.Field()  # 爬取时间

class HospitalItem(scrapy.Item):
    # 医院基本信息
    name = scrapy.Field()  # 医院名称
    level = scrapy.Field()  # 医院等级
    address = scrapy.Field()  # 地址
    phone = scrapy.Field()  # 电话
    website = scrapy.Field()  # 官网
    description = scrapy.Field()  # 简介
    rating = scrapy.Field()  # 评分
    review_count = scrapy.Field()  # 评价数量
    latitude = scrapy.Field()  # 纬度
    longitude = scrapy.Field()  # 经度
    source_url = scrapy.Field()  # 来源URL
    crawl_time = scrapy.Field()  # 爬取时间

class AppointmentItem(scrapy.Item):
    # 预约信息
    doctor_name = scrapy.Field()  # 医生姓名
    doctor_title = scrapy.Field()  # 医生职称
    department = scrapy.Field()  # 科室
    hospital = scrapy.Field()  # 医院
    appointment_date = scrapy.Field()  # 预约日期
    appointment_time = scrapy.Field()  # 预约时间
    available_count = scrapy.Field()  # 可预约数量
    registration_fee = scrapy.Field()  # 挂号费
    source_url = scrapy.Field()  # 来源URL
    crawl_time = scrapy.Field()  # 爬取时间

class SpecialistItem(scrapy.Item):
    # 专家号源信息
    doctor_name = scrapy.Field()  # 专家姓名
    title = scrapy.Field()  # 职称
    department = scrapy.Field()  # 科室
    hospital = scrapy.Field()  # 医院
    appointment_date = scrapy.Field()  # 出诊日期
    appointment_time = scrapy.Field()  # 出诊时间
    registration_type = scrapy.Field()  # 挂号类型（普通/专家/特需）
    available_count = scrapy.Field()  # 可预约数量
    registration_fee = scrapy.Field()  # 挂号费
    source_url = scrapy.Field()  # 来源URL
    crawl_time = scrapy.Field()  # 爬取时间