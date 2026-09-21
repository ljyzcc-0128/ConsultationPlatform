from flask import Blueprint, request, jsonify
from app.services.hospital_service import HospitalService

hospital_bp = Blueprint('hospitals', __name__)

@hospital_bp.route('', methods=['GET'])
def get_hospitals():
    """获取医院列表"""
    try:
        page = request.args.get('page', 1, type=int)
        per_page = request.args.get('per_page', 10, type=int)

        filters = {}
        if request.args.get('name'):
            filters['name'] = request.args.get('name')
        if request.args.get('level'):
            filters['level'] = request.args.get('level')
        if request.args.get('min_rating'):
            filters['min_rating'] = request.args.get('min_rating', type=float)

        result = HospitalService.get_hospitals(page, per_page, filters)
        return jsonify(result)
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@hospital_bp.route('/<int:hospital_id>', methods=['GET'])
def get_hospital(hospital_id):
    """获取单个医院信息"""
    try:
        hospital = HospitalService.get_hospital_by_id(hospital_id)
        if not hospital:
            return jsonify({'error': 'Hospital not found'}), 404
        return jsonify(hospital.to_dict())
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@hospital_bp.route('', methods=['POST'])
def create_hospital():
    """创建新医院"""
    try:
        data = request.get_json()

        # 验证必填字段
        if not data.get('name'):
            return jsonify({'error': 'Name is required'}), 400

        hospital = HospitalService.create_hospital(data)
        return jsonify({'message': 'Hospital created successfully', 'hospital': hospital.to_dict()}), 201
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@hospital_bp.route('/<int:hospital_id>', methods=['PUT'])
def update_hospital(hospital_id):
    """更新医院信息"""
    try:
        data = request.get_json()
        hospital = HospitalService.update_hospital(hospital_id, data)
        if not hospital:
            return jsonify({'error': 'Hospital not found'}), 404
        return jsonify({'message': 'Hospital updated successfully', 'hospital': hospital.to_dict()})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@hospital_bp.route('/<int:hospital_id>', methods=['DELETE'])
def delete_hospital(hospital_id):
    """删除医院"""
    try:
        success = HospitalService.delete_hospital(hospital_id)
        if not success:
            return jsonify({'error': 'Hospital not found'}), 404
        return jsonify({'message': 'Hospital deleted successfully'})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@hospital_bp.route('/search', methods=['GET'])
def search_hospitals():
    """搜索医院"""
    try:
        keyword = request.args.get('keyword', '')
        if not keyword:
            return jsonify({'error': 'Keyword is required'}), 400

        hospitals = HospitalService.search_hospitals(keyword)
        return jsonify({'hospitals': [hospital.to_dict() for hospital in hospitals]})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@hospital_bp.route('/by-level/<level>', methods=['GET'])
def get_hospitals_by_level(level):
    """根据等级获取医院"""
    try:
        hospitals = HospitalService.get_hospitals_by_level(level)
        return jsonify({'hospitals': [hospital.to_dict() for hospital in hospitals]})
    except Exception as e:
        return jsonify({'error': str(e)}), 500