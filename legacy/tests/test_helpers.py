import pytest
from app.utils.helpers import validate_phone, validate_email, format_date, format_time, is_weekend

class TestHelpers:
    def test_validate_phone(self):
        """测试手机号验证"""
        # 有效手机号
        assert validate_phone('13812345678') == True
        assert validate_phone('15987654321') == True
        assert validate_phone('18611112222') == True

        # 无效手机号
        assert validate_phone('1234567890') == False  # 不是1开头
        assert validate_phone('123456789') == False   # 位数不足
        assert validate_phone('138123456789') == False # 位数过多
        assert validate_phone('abc123456') == False    # 包含字母

    def test_validate_email(self):
        """测试邮箱验证"""
        # 有效邮箱
        assert validate_email('test@example.com') == True
        assert validate_email('user.name@domain.co.uk') == True
        assert validate_email('123@qq.com') == True

        # 无效邮箱
        assert validate_email('test@') == False
        assert validate_email('@example.com') == False
        assert validate_email('test.example.com') == False
        assert validate_email('test.example@com') == False

    def test_format_date(self):
        """测试日期格式化"""
        # 有效日期格式
        result = format_date('2024-01-20')
        assert result is not None
        assert result.year == 2024
        assert result.month == 1
        assert result.day == 20

        # 无效日期格式
        assert format_date('20-01-2024') is None  # 格式错误
        assert format_date('invalid') is None     # 无效输入

    def test_format_time(self):
        """测试时间格式化"""
        # 有效时间格式
        result = format_time('14:30')
        assert result is not None
        assert result.hour == 14
        assert result.minute == 30

        # 无效时间格式
        assert format_time('30:14') is None  # 无效时间
        assert format_time('invalid') is None # 无效输入

    def test_is_weekend(self):
        """测试是否是周末"""
        from datetime import datetime, timedelta

        # 周一 (2024-01-15)
        monday = datetime(2024, 1, 15).date()
        assert is_weekend(monday) == False

        # 周日 (2024-01-14)
        sunday = datetime(2024, 1, 14).date()
        assert is_weekend(sunday) == True

        # 周六 (2024-01-13)
        saturday = datetime(2024, 1, 13).date()
        assert is_weekend(saturday) == True