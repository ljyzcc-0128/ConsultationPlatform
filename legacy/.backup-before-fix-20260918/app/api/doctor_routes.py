from flask import Blueprint, request, jsonify
from app.services.doctor_service import DoctorService
from app.utils.helpers import validate_phone

doctor_bp = Blueprint('doctors', __name__)

@doctor_bp.route('', methods=['GET'])
def get_doctors():
    """获取医生列表"""
    try:
        page = request.args.get('page', 1, type=int)
        per_page = request.args.get('per_page', 10, type=int)

        filters = {}
        if request.args.get('name'):
            filters['name'] = request.args.get('name')
        if request.args.get('department'):
            filters['department'] = request.args.get('department')
        if request.args.get('hospital_id'):
            filters['hospital_id'] = request.args.get('hospital_id', type=int)
        if request.args.get('specialty'):
            filters['specialty'] = request.args.get('specialty')
        if request.args.get('min_rating'):
            filters['min_rating'] = request.args.get('min_rating', type=float)

        result = DoctorService.get_doctors(page, per_page, filters)
        return jsonify(result)
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@doctor_bp.route('/<int:doctor_id>', methods=['GET'])
def get_doctor(doctor_id):
    """获取单个医生信息"""
    try:
        doctor = DoctorService.get_doctor_by_id(doctor_id)
        if not doctor:
            return jsonify({'error': 'Doctor not found'}), 404
        return jsonify(doctor.to_dict())
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@doctor_bp.route('', methods=['POST'])
def create_doctor():
    """创建新医生"""
    try:
        data = request.get_json()

        # 验证必填字段
        if not data.get('name'):
            return jsonify({'error': 'Name is required'}), 400
        if not data.get('department'):
            return jsonify({'error': 'Department is required'}), 400

        doctor = DoctorService.create_doctor(data)
        return jsonify({'message': 'Doctor created successfully', 'doctor': doctor.to_dict()}), 201
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@doctor_bp.route('/<int:doctor_id>', methods=['PUT'])
def update_doctor(doctor_id):
    """更新医生信息"""
    try:
        data = request.get_json()
        doctor = DoctorService.update_doctor(doctor_id, data)
        if not doctor:
            return jsonify({'error': 'Doctor not found'}), 404
        return jsonify({'message': 'Doctor updated successfully', 'doctor': doctor.to_dict()})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@doctor_bp.route('/<int:doctor_id>', methods=['DELETE'])
def delete_doctor(doctor_id):
    """删除医生"""
    try:
        success = DoctorService.delete_doctor(doctor_id)
        if not success:
            return jsonify({'error': 'Doctor not found'}), 404
        return jsonify({'message': 'Doctor deleted successfully'})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@doctor_bp.route('/search', methods=['GET'])
def search_doctors():
    """搜索医生"""
    try:
        keyword = request.args.get('keyword', '')
        if not keyword:
            return jsonify({'error': 'Keyword is required'}), 400

        doctors = DoctorService.search_doctors(keyword)
        return jsonify({'doctors': [doctor.to_dict() for doctor in doctors]})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@doctor_bp.route('/by-department/<department>', methods=['GET'])
def get_doctors_by_department(department):
    """根据科室获取医生"""
    try:
        doctors = DoctorService.get_doctors_by_department(department)
        return jsonify({'doctors': [doctor.to_dict() for doctor in doctors]})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@doctor_bp.route('/by-hospital/<int:hospital_id>', methods=['GET'])
def get_doctors_by_hospital(hospital_id):
    """根据医院获取医生"""
    try:
        doctors = DoctorService.get_doctors_by_hospital(hospital_id)
        return jsonify({'doctors': [doctor.to_dict() for doctor in doctors]})
    except Exception as e:
        return jsonify({'error': str(e)}), 500