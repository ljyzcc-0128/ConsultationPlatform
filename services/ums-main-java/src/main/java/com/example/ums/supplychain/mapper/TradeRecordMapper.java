package com.example.ums.supplychain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.supplychain.model.CpTradeRecord;
import org.apache.ibatis.annotations.Mapper;

/** 贸易物流记录 Mapper。 */
@Mapper
public interface TradeRecordMapper extends BaseMapper<CpTradeRecord> {
}
