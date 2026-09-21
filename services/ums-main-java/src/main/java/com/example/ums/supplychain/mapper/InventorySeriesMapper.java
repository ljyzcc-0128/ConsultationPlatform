package com.example.ums.supplychain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.supplychain.model.CpInventorySeries;
import org.apache.ibatis.annotations.Mapper;

/** 库存看板序列 Mapper。 */
@Mapper
public interface InventorySeriesMapper extends BaseMapper<CpInventorySeries> {
}
