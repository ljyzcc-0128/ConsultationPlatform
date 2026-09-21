package com.example.ums.push.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ums.push.model.PushMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 推送消息 Mapper（push_messages）。 */
@Mapper
public interface PushMessageMapper extends BaseMapper<PushMessage> {

    /** 查询指定用户的推送消息列表。 */
    @Select("SELECT * FROM push_messages WHERE user_id = #{userId} ORDER BY sent_at DESC")
    List<PushMessage> findByUserId(@Param("userId") String userId);

    /** 统计指定任务的推送消息总数。 */
    @Select("SELECT COUNT(*) FROM push_messages WHERE task_id = #{taskId}")
    long countByTaskId(@Param("taskId") String taskId);

    /** 统计指定任务的已送达消息数。 */
    @Select("SELECT COUNT(*) FROM push_messages WHERE task_id = #{taskId} AND status = 'DELIVERED'")
    long countDeliveredByTaskId(@Param("taskId") String taskId);

    /** 统计指定任务的已点击消息数。 */
    @Select("SELECT COUNT(*) FROM push_messages WHERE task_id = #{taskId} AND status = 'CLICKED'")
    long countClickedByTaskId(@Param("taskId") String taskId);
}
