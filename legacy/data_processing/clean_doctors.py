#!/usr/bin/env python3
"""
医生数据清洗脚本
"""

import pandas as pd
import re
import json
from datetime import datetime
import os

def clean_doctor_data():
    """清洗医生数据"""
    # 数据路径
    raw_data_path = '../data/raw'
    processed_data_path = '../data/processed'

    # 确保目录存在
    os.makedirs(processed_data_path, exist_ok=True)

    # 获取所有JSON文件
    json_files = [f for f in os.listdir(raw_data_path) if f.endswith('.json') and 'doctor' in f.lower()]

    all_cleaned_data = []

    for json_file in json_files:
        file_path = os.path.join(raw_data_path, json_file)

        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)

                if isinstance(data, list):
                    # 如果是列表，处理每个医生
                    for item in data:
                        cleaned = clean_doctor_item(item)
                        if cleaned:
                            all_cleaned_data.append(cleaned)
                else:
                    # 如果是单个医生，直接处理
                    cleaned = clean_doctor_item(data)
                    if cleaned:
                        all_cleaned_data.append(cleaned)

        except Exception as e:
            print(f"处理文件 {json_file} 时出错: {e}")

    # 保存清洗后的数据
    if all_cleaned_data:
        df = pd.DataFrame(all_cleaned_data)

        # 保存为CSV
        csv_path = os.path.join(processed_data_path, 'cleaned_doctors.csv')
        df.to_csv(csv_path, index=False, encoding='utf-8-sig')

        # 保存为JSON
        json_path = os.path.join(processed_data_path, 'cleaned_doctors.json')
        df.to_json(json_path, orient='records', force_ascii=False, indent=2)

        print(f"清洗完成！共处理 {len(all_cleaned_data)} 条医生数据")
        print(f"数据已保存到:")
        print(f"  - CSV: {csv_path}")
        print(f"  - JSON: {json_path}")

        # 生成统计报告
        generate_statistics(df)

    else:
        print("没有找到医生数据")

def clean_doctor_item(item):
    """清洗单个医生数据"""
    try:
        cleaned = {
            'name': clean_text(item.get('name', '')),
            'title': clean_text(item.get('title', '')),
            'department': clean_text(item.get('department', '')),
            'hospital': clean_text(item.get('hospital', '')),
            'specialty': clean_text(item.get('specialty', '')),
            'description': clean_text(item.get('description', '')),
            'photo_url': clean_url(item.get('photo_url', '')),
            'consultation_fee': clean_money(item.get('consultation_fee', 0)),
            'experience_years': clean_experience(item.get('experience_years', 0)),
            'education': clean_text(item.get('education', '')),
            'licenses': clean_text(item.get('licenses', '')),
            'rating': clean_rating(item.get('rating', 0)),
            'review_count': clean_count(item.get('review_count', 0)),
            'source_url': clean_url(item.get('source_url', '')),
            'crawl_time': clean_datetime(item.get('crawl_time', ''))
        }

        # 验证必填字段
        if not cleaned['name'] or not cleaned['department']:
            return None

        return cleaned

    except Exception as e:
        print(f"清洗医生数据时出错: {e}")
        return None

def clean_text(text):
    """清洗文本数据"""
    if not isinstance(text, str):
        return ''

    # 去除首尾空格
    text = text.strip()

    # 替换多个连续空格为单个空格
    text = re.sub(r'\s+', ' ', text)

    # 去除特殊字符
    text = re.sub(r'[^\w\s一-鿿.,;:!?()]', '', text)

    return text

def clean_url(url):
    """清洗URL"""
    if not isinstance(url, str):
        return ''

    url = url.strip()

    # 简单的URL验证
    if url.startswith(('http://', 'https://')):
        return url
    else:
        return ''

def clean_money(value):
    """清洗金额"""
    try:
        if isinstance(value, (int, float)):
            return float(value)
        elif isinstance(value, str):
            # 提取数字部分
            match = re.search(r'(\d+\.?\d*)', value)
            if match:
                return float(match.group(1))
        return 0.0
    except:
        return 0.0

def clean_experience(value):
    """清洗从医年限"""
    try:
        if isinstance(value, int):
            return int(value)
        elif isinstance(value, str):
            # 提取数字部分
            match = re.search(r'(\d+)', value)
            if match:
                return int(match.group(1))
        return 0
    except:
        return 0

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

def generate_statistics(df):
    """生成统计报告"""
    stats = {
        'total_doctors': len(df),
        'departments': df['department'].nunique(),
        'hospitals': df['hospital'].nunique(),
        'avg_rating': df['rating'].mean(),
        'avg_fee': df['consultation_fee'].mean(),
        'experience_distribution': {
            '<5年': len(df[df['experience_years'] < 5]),
            '5-10年': len(df[(df['experience_years'] >= 5) & (df['experience_years'] < 10)]),
            '10-20年': len(df[(df['experience_years'] >= 10) & (df['experience_years'] < 20)]),
            '>20年': len(df[df['experience_years'] >= 20])
        },
        'rating_distribution': {
            '1-2分': len(df[(df['rating'] >= 1) & (df['rating'] < 2)]),
            '2-3分': len(df[(df['rating'] >= 2) & (df['rating'] < 3)]),
            '3-4分': len(df[(df['rating'] >= 3) & (df['rating'] < 4)]),
            '4-5分': len(df[df['rating'] >= 4])
        }
    }

    # 保存统计报告
    stats_path = '../data/processed/doctor_statistics.json'
    with open(stats_path, 'w', encoding='utf-8') as f:
        json.dump(stats, f, ensure_ascii=False, indent=2, default=str)

    print("\n=== 医生数据统计报告 ===")
    print(f"总医生数: {stats['total_doctors']}")
    print(f"科室数: {stats['departments']}")
    print(f"医院数: {stats['hospitals']}")
    print(f"平均评分: {stats['avg_rating']:.2f}")
    print(f"平均诊费: {stats['avg_fee']:.2f}元")
    print("\n从医年限分布:")
    for period, count in stats['experience_distribution'].items():
        print(f"  {period}: {count}人")
    print("\n评分分布:")
    for rating_range, count in stats['rating_distribution'].items():
        print(f"  {rating_range}: {count}人")

if __name__ == '__main__':
    clean_doctor_data()