#!/usr/bin/env python3
"""
医院数据清洗脚本
"""

import pandas as pd
import re
import json
from datetime import datetime
import os

def clean_hospital_data():
    """清洗医院数据"""
    # 数据路径
    raw_data_path = '../data/raw'
    processed_data_path = '../data/processed'

    # 确保目录存在
    os.makedirs(processed_data_path, exist_ok=True)

    # 获取所有JSON文件
    json_files = [f for f in os.listdir(raw_data_path) if f.endswith('.json') and 'hospital' in f.lower()]

    all_cleaned_data = []

    for json_file in json_files:
        file_path = os.path.join(raw_data_path, json_file)

        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)

                if isinstance(data, list):
                    # 如果是列表，处理每个医院
                    for item in data:
                        cleaned = clean_hospital_item(item)
                        if cleaned:
                            all_cleaned_data.append(cleaned)
                else:
                    # 如果是单个医院，直接处理
                    cleaned = clean_hospital_item(data)
                    if cleaned:
                        all_cleaned_data.append(cleaned)

        except Exception as e:
            print(f"处理文件 {json_file} 时出错: {e}")

    # 保存清洗后的数据
    if all_cleaned_data:
        df = pd.DataFrame(all_cleaned_data)

        # 保存为CSV
        csv_path = os.path.join(processed_data_path, 'cleaned_hospitals.csv')
        df.to_csv(csv_path, index=False, encoding='utf-8-sig')

        # 保存为JSON
        json_path = os.path.join(processed_data_path, 'cleaned_hospitals.json')
        df.to_json(json_path, orient='records', force_ascii=False, indent=2)

        print(f"清洗完成！共处理 {len(all_cleaned_data)} 条医院数据")
        print(f"数据已保存到:")
        print(f"  - CSV: {csv_path}")
        print(f"  - JSON: {json_path}")

        # 生成统计报告
        generate_hospital_statistics(df)

    else:
        print("没有找到医院数据")

def clean_hospital_item(item):
    """清洗单个医院数据"""
    try:
        cleaned = {
            'name': clean_text(item.get('name', '')),
            'level': clean_level(item.get('level', '')),
            'address': clean_address(item.get('address', '')),
            'phone': clean_phone(item.get('phone', '')),
            'website': clean_url(item.get('website', '')),
            'description': clean_text(item.get('description', '')),
            'rating': clean_rating(item.get('rating', 0)),
            'review_count': clean_count(item.get('review_count', 0)),
            'latitude': clean_coordinate(item.get('latitude')),
            'longitude': clean_coordinate(item.get('longitude')),
            'source_url': clean_url(item.get('source_url', '')),
            'crawl_time': clean_datetime(item.get('crawl_time', ''))
        }

        # 验证必填字段
        if not cleaned['name'] or not cleaned['level']:
            return None

        return cleaned

    except Exception as e:
        print(f"清洗医院数据时出错: {e}")
        return None

def clean_text(text):
    """清洗文本数据"""
    if not isinstance(text, str):
        return ''

    # 去除首尾空格
    text = text.strip()

    # 替换多个连续空格为单个空格
    text = re.sub(r'\s+', ' ', text)

    # 去除特殊字符，保留中文、英文、数字和基本标点
    text = re.sub(r'[^\w\s一-鿿.,;:!?()-]', '', text)

    return text

def clean_level(level):
    """清洗医院等级"""
    level_mapping = {
        '三级甲等': '三级甲等',
        '三级': '三级甲等',
        '三甲': '三级甲等',
        '二级甲等': '二级甲等',
        '二级': '二级甲等',
        '二甲': '二级甲等',
        '一级甲等': '一级甲等',
        '一级': '一级甲等',
        '一甲': '一级甲等',
        '未定级': '未定级',
        '其他': '其他'
    }

    if not level:
        return ''

    # 转换为小写进行匹配
    level_lower = level.lower().strip()

    # 尝试直接匹配
    if level in level_mapping:
        return level_mapping[level]

    # 尝试模糊匹配
    for key, value in level_mapping.items():
        if key.lower() in level_lower or level_lower in key.lower():
            return value

    return level

def clean_address(address):
    """清洗地址数据"""
    if not isinstance(address, str):
        return ''

    address = address.strip()

    # 标准化地址格式
    # 移除多余的标点符号
    address = re.sub(r'[,,，、]+', '，', address)

    # 确保省市区格式统一
    if '省' in address and '市' not in address:
        # 如果有省没有市，可能需要补充
        pass

    return address

def clean_phone(phone):
    """清洗电话号码"""
    if not isinstance(phone, str):
        return ''

    # 移除所有非数字字符
    phone_digits = re.sub(r'[^\d]', '', phone)

    # 检查手机号格式
    if len(phone_digits) == 11 and phone_digits.startswith('1'):
        return phone_digits

    # 检查固定电话格式（假设是区号+号码）
    if len(phone_digits) >= 7:
        # 可以添加更多的电话号码验证逻辑
        return phone_digits

    return ''

def clean_url(url):
    """清洗URL"""
    if not isinstance(url, str):
        return ''

    url = url.strip()

    # 简单的URL验证
    if url.startswith(('http://', 'https://')):
        return url
    elif url.startswith('www.'):
        return 'https://' + url

    return ''

def clean_rating(value):
    """清洗评分"""
    try:
        rating = float(value)
        # 评分范围0-5
        return max(0.0, min(5.0, rating))
    except:
        return 0.0

def clean_count(value):
    """清洗数量"""
    try:
        return int(max(0, value))
    except:
        return 0

def clean_coordinate(coord):
    """清洗坐标"""
    try:
        coord = float(coord)
        # 简单的经纬度范围检查
        if -180 <= coord <= 180:
            return coord
        else:
            return None
    except:
        return None

def clean_datetime(dt_str):
    """清洗时间"""
    if not dt_str:
        return ''

    try:
        # 尝试解析时间字符串
        if isinstance(dt_str, str):
            # 尝试不同格式
            for fmt in ['%Y-%m-%d %H:%M:%S', '%Y-%m-%dT%H:%M:%S', '%Y-%m-%d']:
                try:
                    dt = datetime.strptime(dt_str, fmt)
                    return dt.strftime('%Y-%m-%d %H:%M:%S')
                except:
                    continue
        return ''
    except:
        return ''

def generate_hospital_statistics(df):
    """生成医院统计报告"""
    stats = {
        'total_hospitals': len(df),
        'levels': df['level'].value_counts().to_dict(),
        'avg_rating': df['rating'].mean(),
        'avg_review_count': df['review_count'].mean(),
        'rating_distribution': {
            '1-2分': len(df[(df['rating'] >= 1) & (df['rating'] < 2)]),
            '2-3分': len(df[(df['rating'] >= 2) & (df['rating'] < 3)]),
            '3-4分': len(df[(df['rating'] >= 3) & (df['rating'] < 4)]),
            '4-5分': len(df[df['rating'] >= 4])
        },
        'location_coverage': {
            'has_coordinates': len(df[(df['latitude'].notna()) & (df['longitude'].notna())]),
            'no_coordinates': len(df[(df['latitude'].isna()) | (df['longitude'].isna())])
        }
    }

    # 保存统计报告
    stats_path = '../data/processed/hospital_statistics.json'
    with open(stats_path, 'w', encoding='utf-8') as f:
        json.dump(stats, f, ensure_ascii=False, indent=2, default=str)

    print("\n=== 医院数据统计报告 ===")
    print(f"总医院数: {stats['total_hospitals']}")
    print(f"\n医院等级分布:")
    for level, count in stats['levels'].items():
        print(f"  {level}: {count}家")
    print(f"\n平均评分: {stats['avg_rating']:.2f}分")
    print(f"平均评价数: {stats['avg_review_count']:.0f}条")
    print("\n评分分布:")
    for rating_range, count in stats['rating_distribution'].items():
        print(f"  {rating_range}: {count}家")
    print("\n坐标覆盖情况:")
    print(f"  有坐标: {stats['location_coverage']['has_coordinates']}家")
    print(f"  无坐标: {stats['location_coverage']['no_coordinates']}家")

if __name__ == '__main__':
    clean_hospital_data()