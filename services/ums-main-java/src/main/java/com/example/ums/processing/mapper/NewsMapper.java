package com.example.ums.processing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.processing.model.CpNews;

/**
 * cp_news 数据访问（MyBatis-Plus BaseMapper，通用 CRUD）。
 *
 * 幂等 upsert 逻辑见 NewsIngestServiceImpl：
 * - LambdaQueryWrapper 按 (source_id, url_hash) 唯一键查询
 * - insert 走 BaseMapper.insert（IdType.INPUT，业务生成 UUID）
 * - updateById 走 NOT_NULL 策略：null 字段跳过，等价 COALESCE(新值, 旧值)
 */
public interface NewsMapper extends BaseMapper<CpNews> {
}
