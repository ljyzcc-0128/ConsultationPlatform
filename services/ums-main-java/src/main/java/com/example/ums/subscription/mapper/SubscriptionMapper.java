package com.example.ums.subscription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.subscription.model.CpSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 用户订阅规则 Mapper。 */
@Mapper
public interface SubscriptionMapper extends BaseMapper<CpSubscription> {

    /** 企业候选：供应商动态表中的企业名单。 */
    @Select("SELECT DISTINCT supplier_name FROM cp_supplier_update "
            + "WHERE supplier_name IS NOT NULL AND supplier_name != '' "
            + "ORDER BY supplier_name LIMIT 200")
    List<String> selectCompanyOptions();

    /** 主题/内容域候选：近期资讯已积累的 AI 分类标签（JSON 数组字符串）。 */
    @Select("SELECT category FROM cp_news WHERE category IS NOT NULL AND category != 'null' "
            + "ORDER BY publish_date DESC LIMIT 800")
    List<String> selectRecentCategories();

    /** 指标候选：供应链指标序列名称。 */
    @Select("SELECT DISTINCT name FROM cp_indicator_series "
            + "WHERE name IS NOT NULL AND name != '' ORDER BY name LIMIT 200")
    List<String> selectIndicatorOptions();

    /** 事件候选：资讯重大变化事件类型。 */
    @Select("SELECT DISTINCT event_type FROM change_events WHERE event_type IS NOT NULL LIMIT 50")
    List<String> selectFeedChangeEventTypes();

    /** 事件候选：供应链指标变化事件类型。 */
    @Select("SELECT DISTINCT change_type FROM cp_change_events WHERE change_type IS NOT NULL LIMIT 50")
    List<String> selectCpChangeEventTypes();
}
