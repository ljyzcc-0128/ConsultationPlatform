import pytest
from app.services.doctor_service import DoctorService
from app.models import db, Doctor
from config import config

class TestDoctorService:
    @pytest.fixture(scope='class')
    def app(self):
        """创建测试应用"""
        from main import create_app
        app = create_app()
        app.config.from_object(config['testing'])
        with app.app_context():
            db.create_all()
            yield app
            db.drop_all()

    def test_create_doctor(self, app):
        """测试创建医生"""
        with app.app_context():
            doctor_data = {
                'name': '张医生',
                'title': '主任医师',
                'department': '内科',
                'hospital_id': 1,
                'specialty': '心血管疾病',
                'consultation_fee': 100.0
            }

            doctor = DoctorService.create_doctor(doctor_data)
            assert doctor.id is not None
            assert doctor.name == '张医生'
            assert doctor.department == '内科'

    def test_get_doctor_by_id(self, app):
        """测试根据ID获取医生"""
        with app.app_context():
            # 先创建一个医生
            doctor_data = {
                'name': '李医生',
                'title': '副主任医师',
                'department': '外科',
                'hospital_id': 1
            }
            doctor = DoctorService.create_doctor(doctor_data)

            # 获取医生
            retrieved_doctor = DoctorService.get_doctor_by_id(doctor.id)
            assert retrieved_doctor is not None
            assert retrieved_doctor.name == '李医生'

    def test_search_doctors(self, app):
        """测试搜索医生"""
        with app.app_context():
            # 创建多个医生
            doctors = [
                {'name': '王医生', 'department': '内科'},
                {'name': '赵医生', 'department': '外科'},
                {'name': '孙医生', 'department': '内科'}
            ]
            for doctor_data in doctors:
                DoctorService.create_doctor(doctor_data)

            # 搜索内科医生
            results = DoctorService.search_doctors('内科')
            assert len(results) >= 2  # 至少有两个内科医生

    def test_get_doctors_by_department(self, app):
        """测试根据科室获取医生"""
        with app.app_context():
            # 创建多个不同科室的医生
            doctors = [
                {'name': '陈医生', 'department': '内科'},
                {'name': '刘医生', 'department': '外科'},
                {'name': '周医生', 'department': '内科'}
            ]
            for doctor_data in doctors:
                DoctorService.create_doctor(doctor_data)

            # 获取内科医生
            internal_doctors = DoctorService.get_doctors_by_department('内科')
            assert len(internal_doctors) == 2
            for doctor in internal_doctors:
                assert doctor.department == '内科'