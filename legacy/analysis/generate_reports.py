#!/usr/bin/env python3
"""
生成分析报告和数据可视化
"""

import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
from datetime import datetime
import os
import json

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

def generate_comprehensive_report():
    """生成综合分析报告"""
    print("开始生成综合分析报告...")

    # 数据路径
    data_path = '../data/processed'

    # 创建报告目录
    report_dir = '../reports'
    os.makedirs(report_dir, exist_ok=True)

    # 读取清洗后的数据
    doctors_df = pd.read_csv(os.path.join(data_path, 'cleaned_doctors.csv'), encoding='utf-8-sig')
    hospitals_df = pd.read_csv(os.path.join(data_path, 'cleaned_hospitals.csv'), encoding='utf-8-sig')

    print(f"读取到 {len(doctors_df)} 条医生数据")
    print(f"读取到 {len(hospitals_df)} 条医院数据")

    # 生成各种图表
    generate_department_analysis(doctors_df, report_dir)
    generate_rating_analysis(doctors_df, report_dir)
    generate_fee_analysis(doctors_df, report_dir)
    generate_experience_analysis(doctors_df, report_dir)
    generate_hospital_analysis(hospitals_df, report_dir)
    generate_comprehensive_summary(doctors_df, hospitals_df, report_dir)

    print(f"\n报告生成完成！保存在 {report_dir} 目录")

def generate_department_analysis(doctors_df, report_dir):
    """生成科室分析"""
    print("生成科室分布分析...")

    # 统计各科室医生数量
    dept_counts = doctors_df['department'].value_counts().head(10)

    # 创建图表
    plt.figure(figsize=(12, 8))
    sns.barplot(x=dept_counts.values, y=dept_counts.index)
    plt.title('Top 10 科室医生分布', fontsize=16, pad=20)
    plt.xlabel('医生数量', fontsize=12)
    plt.ylabel('科室', fontsize=12)
    plt.grid(axis='x', alpha=0.3)

    # 保存图表
    chart_path = os.path.join(report_dir, 'department_distribution.png')
    plt.tight_layout()
    plt.savefig(chart_path, dpi=300, bbox_inches='tight')
    plt.close()

    # 保存数据
    dept_data = dept_counts.reset_index()
    dept_data.columns = ['department', 'doctor_count']
    dept_path = os.path.join(report_dir, 'department_stats.csv')
    dept_data.to_csv(dept_path, index=False, encoding='utf-8-sig')

    print(f"  科室分布图表已保存: {chart_path}")

def generate_rating_analysis(doctors_df, report_dir):
    """生成评分分析"""
    print("生成评分分析...")

    # 评分分布
    plt.figure(figsize=(12, 6))

    # 直方图
    plt.subplot(1, 2, 1)
    sns.histplot(doctors_df['rating'], bins=20, kde=True)
    plt.title('医生评分分布', fontsize=14)
    plt.xlabel('评分', fontsize=12)
    plt.ylabel('数量', fontsize=12)

    # 箱线图
    plt.subplot(1, 2, 2)
    sns.boxplot(y=doctors_df['rating'])
    plt.title('医生评分箱线图', fontsize=14)
    plt.ylabel('评分', fontsize=12)

    plt.tight_layout()
    chart_path = os.path.join(report_dir, 'rating_analysis.png')
    plt.savefig(chart_path, dpi=300, bbox_inches='tight')
    plt.close()

    # 评分统计
    rating_stats = {
        'mean_rating': doctors_df['rating'].mean(),
        'median_rating': doctors_df['rating'].median(),
        'max_rating': doctors_df['rating'].max(),
        'min_rating': doctors_df['rating'].min(),
        'std_rating': doctors_df['rating'].std()
    }

    with open(os.path.join(report_dir, 'rating_stats.json'), 'w', encoding='utf-8') as f:
        json.dump(rating_stats, f, ensure_ascii=False, indent=2)

    print(f"  评分分析图表已保存: {chart_path}")

def generate_fee_analysis(doctors_df, report_dir):
    """生成诊费分析"""
    print("生成诊费分析...")

    # 过滤掉诊费为0的记录
    fee_df = doctors_df[doctors_df['consultation_fee'] > 0]

    if len(fee_df) > 0:
        plt.figure(figsize=(15, 10))

        # 诊费分布
        plt.subplot(2, 2, 1)
        sns.histplot(fee_df['consultation_fee'], bins=30, kde=True)
        plt.title('诊费分布', fontsize=14)
        plt.xlabel('诊费（元）', fontsize=12)
        plt.ylabel('数量', fontsize=12)

        # 诊费与评分关系
        plt.subplot(2, 2, 2)
        sns.scatterplot(x='consultation_fee', y='rating', data=fee_df, alpha=0.6)
        plt.title('诊费与评分关系', fontsize=14)
        plt.xlabel('诊费（元）', fontsize=12)
        plt.ylabel('评分', fontsize=12)

        # 各科室平均诊费
        plt.subplot(2, 2, 3)
        dept_fees = fee_df.groupby('department')['consultation_fee'].mean().sort_values(ascending=False).head(10)
        sns.barplot(x=dept_fees.values, y=dept_fees.index)
        plt.title('各科室平均诊费Top 10', fontsize=14)
        plt.xlabel('平均诊费（元）', fontsize=12)
        plt.ylabel('科室', fontsize=12)

        # 诊费箱线图
        plt.subplot(2, 2, 4)
        fee_ranges = pd.cut(fee_df['consultation_fee'], bins=[0, 50, 100, 200, 500, float('inf')], labels=['<50', '50-100', '100-200', '200-500', '>500'])
        sns.boxplot(x=fee_ranges, y=fee_df['consultation_fee'])
        plt.title('不同价格区间诊费分布', fontsize=14)
        plt.xlabel('价格区间', fontsize=12)
        plt.ylabel('诊费（元）', fontsize=12)
        plt.xticks(rotation=45)

        plt.tight_layout()
        chart_path = os.path.join(report_dir, 'fee_analysis.png')
        plt.savefig(chart_path, dpi=300, bbox_inches='tight')
        plt.close()

        # 保存诊费统计
        fee_stats = {
            'avg_fee': fee_df['consultation_fee'].mean(),
            'median_fee': fee_df['consultation_fee'].median(),
            'max_fee': fee_df['consultation_fee'].max(),
            'min_fee': fee_df['consultation_fee'].min(),
            'total_doctors_with_fee': len(fee_df)
        }

        with open(os.path.join(report_dir, 'fee_stats.json'), 'w', encoding='utf-8') as f:
            json.dump(fee_stats, f, ensure_ascii=False, indent=2)

        print(f"  诊费分析图表已保存: {chart_path}")
    else:
        print("  无诊费数据可供分析")

def generate_experience_analysis(doctors_df, report_dir):
    """生成从医经验分析"""
    print("生成从医经验分析...")

    plt.figure(figsize=(15, 10))

    # 经验分布
    plt.subplot(2, 2, 1)
    sns.histplot(doctors_df['experience_years'], bins=20, kde=True)
    plt.title('从医年限分布', fontsize=14)
    plt.xlabel('从医年限', fontsize=12)
    plt.ylabel('数量', fontsize=12)

    # 经验与评分关系
    plt.subplot(2, 2, 2)
    sns.scatterplot(x='experience_years', y='rating', data=doctors_df, alpha=0.6)
    plt.title('从医经验与评分关系', fontsize=14)
    plt.xlabel('从医年限', fontsize=12)
    plt.ylabel('评分', fontsize=12)

    # 经验与诊费关系
    plt.subplot(2, 2, 3)
    fee_df = doctors_df[doctors_df['consultation_fee'] > 0]
    if len(fee_df) > 0:
        sns.scatterplot(x='experience_years', y='consultation_fee', data=fee_df, alpha=0.6)
        plt.title('从医经验与诊费关系', fontsize=14)
        plt.xlabel('从医年限', fontsize=12)
        plt.ylabel('诊费（元）', fontsize=12)

    # 经验分布饼图
    plt.subplot(2, 2, 4)
    exp_ranges = pd.cut(doctors_df['experience_years'],
                       bins=[0, 5, 10, 20, float('inf')],
                       labels=['<5年', '5-10年', '10-20年', '>20年'])
    exp_counts = exp_ranges.value_counts()
    plt.pie(exp_counts.values, labels=exp_counts.index, autopct='%1.1f%%')
    plt.title('从医经验分布', fontsize=14)

    plt.tight_layout()
    chart_path = os.path.join(report_dir, 'experience_analysis.png')
    plt.savefig(chart_path, dpi=300, bbox_inches='tight')
    plt.close()

    print(f"  经验分析图表已保存: {chart_path}")

def generate_hospital_analysis(hospitals_df, report_dir):
    """生成医院分析"""
    print("生成医院分析...")

    plt.figure(figsize=(15, 10))

    # 医院等级分布
    plt.subplot(2, 2, 1)
    level_counts = hospitals_df['level'].value_counts()
    plt.pie(level_counts.values, labels=level_counts.index, autopct='%1.1f%%')
    plt.title('医院等级分布', fontsize=14)

    # 医院评分分布
    plt.subplot(2, 2, 2)
    sns.histplot(hospitals_df['rating'], bins=20, kde=True)
    plt.title('医院评分分布', fontsize=14)
    plt.xlabel('评分', fontsize=12)
    plt.ylabel('数量', fontsize=12)

    # 医院评分 vs 评价数量
    plt.subplot(2, 2, 3)
    sns.scatterplot(x='review_count', y='rating', data=hospitals_df, alpha=0.6)
    plt.title('医院评分 vs 评价数量', fontsize=14)
    plt.xlabel('评价数量', fontsize=12)
    plt.ylabel('评分', fontsize=12)

    # Top 10 医院（按评分）
    plt.subplot(2, 2, 4)
    top_hospitals = hospitals_df.nlargest(10, 'rating')
    sns.barplot(x='rating', y='name', data=top_hospitals)
    plt.title('Top 10 高评分医院', fontsize=14)
    plt.xlabel('评分', fontsize=12)
    plt.ylabel('医院名称', fontsize=12)

    plt.tight_layout()
    chart_path = os.path.join(report_dir, 'hospital_analysis.png')
    plt.savefig(chart_path, dpi=300, bbox_inches='tight')
    plt.close()

    print(f"  医院分析图表已保存: {chart_path}")

def generate_comprehensive_summary(doctors_df, hospitals_df, report_dir):
    """生成综合摘要"""
    print("生成综合摘要...")

    summary = {
        'report_date': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
        'total_doctors': len(doctors_df),
        'total_hospitals': len(hospitals_df),
        'departments': doctors_df['department'].nunique(),
        'hospital_levels': hospitals_df['level'].nunique(),
        'avg_doctor_rating': doctors_df['rating'].mean(),
        'avg_hospital_rating': hospitals_df['rating'].mean(),
        'avg_consultation_fee': doctors_df['consultation_fee'].mean(),
        'avg_experience_years': doctors_df['experience_years'].mean(),
        'top_department': doctors_df['department'].value_counts().index[0] if len(doctors_df) > 0 else None,
        'top_hospital_level': hospitals_df['level'].value_counts().index[0] if len(hospitals_df) > 0 else None,
        'data_insights': {
            'high_rating_doctors': len(doctors_df[doctors_df['rating'] >= 4.5]),
            'high_fee_doctors': len(doctors_df[doctors_df['consultation_fee'] >= 200]),
            'experienced_doctors': len(doctors_df[doctors_df['experience_years'] >= 20]),
            'top_rated_hospitals': len(hospitals_df[hospitals_df['rating'] >= 4.5])
        }
    }

    # 保存摘要
    with open(os.path.join(report_dir, 'comprehensive_summary.json'), 'w', encoding='utf-8') as f:
        json.dump(summary, f, ensure_ascii=False, indent=2, default=str)

    # 生成摘要报告文本
    report_text = f"""
    === 咨询平台数据分析报告 ===

    报告生成时间: {summary['report_date']}

    === 基本统计 ===
    - 医生总数: {summary['total_doctors']} 人
    - 医院总数: {summary['total_hospitals']} 家
    - 科室数量: {summary['departments']} 个
    - 医院等级数: {summary['hospital_levels']} 种

    === 平均值统计 ===
    - 医生平均评分: {summary['avg_doctor_rating']:.2f} 分
    - 医院平均评分: {summary['avg_hospital_rating']:.2f} 分
    - 平均诊费: {summary['avg_consultation_fee']:.2f} 元
    - 平均从医年限: {summary['avg_experience_years']:.1f} 年

    === 分布特征 ===
    - 主要科室: {summary['top_department']}
    - 主要医院等级: {summary['top_hospital_level']}

    === 特色数据 ===
    - 高评分医生 (≥4.5分): {summary['data_insights']['high_rating_doctors']} 人
    - 高诊费医生 (≥200元): {summary['data_insights']['high_fee_doctors']} 人
    - 经验丰富医生 (≥20年): {summary['data_insights']['experienced_doctors']} 人
    - 高评分医院 (≥4.5分): {summary['data_insights']['top_rated_hospitals']} 家

    === 建议 ===
    1. 关注高评分医生的分布和特征
    2. 分析不同科室的诊费差异
    3. 探索从医经验与医术水平的关系
    4. 监控医院服务质量的提升
    """

    # 保存文本报告
    with open(os.path.join(report_dir, 'analysis_report.txt'), 'w', encoding='utf-8') as f:
        f.write(report_text)

    print("\n" + report_text)
    print(f"  综合摘要已保存: {os.path.join(report_dir, 'comprehensive_summary.json')}")
    print(f"  文本报告已保存: {os.path.join(report_dir, 'analysis_report.txt')}")

if __name__ == '__main__':
    generate_comprehensive_report()