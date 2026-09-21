package com.example.ums.feed.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.feed.model.CpReadStatus;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户已读状态 Mapper（cp_read_status，复合主键）。
 */
@Mapper
public interface ReadStatusMapper extends BaseMapper<CpReadStatus> {

    /** 查询指定用户在给定文章ID中已读的文章ID集合。 */
    @Select({
            "<script>",
            "SELECT article_id FROM cp_read_status WHERE user_id = #{userId}",
            "AND article_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    List<String> findReadArticleIds(@Param("userId") String userId, @Param("ids") List<String> ids);

    /** 批量标记已读（INSERT IGNORE，重复主键自动忽略）。 */
    @Insert({
            "<script>",
            "INSERT IGNORE INTO cp_read_status (user_id, article_id) VALUES",
            "<foreach collection='ids' item='id' separator=','>",
            "(#{userId}, #{id})",
            "</foreach>",
            "</script>"
    })
    void batchMarkRead(@Param("userId") String userId, @Param("ids") List<String> ids);

    /** 清除指定用户全部已读状态。 */
    @Delete("DELETE FROM cp_read_status WHERE user_id = #{userId}")
    void resetAll(@Param("userId") String userId);
}
