package com.example.ums.admin.service;

import com.example.ums.admin.dto.DlqMessageView;
import com.example.ums.admin.dto.DlqQueueStatus;

import java.util.List;

/** 异常处理：死信队列查看/重放/清空（ADM-002/004）。 */
public interface AdminDlqService {

    /** 三个业务死信队列的深度。 */
    List<DlqQueueStatus> queues();

    /** 预览死信消息（peek，不消费）。 */
    List<DlqMessageView> messages(String queue, int count);

    /** 重放：取出全部死信并重新发布到原队列（返回重放条数）。 */
    int requeue(String queue);

    /** 清空指定死信队列（返回清空前条数）。 */
    long purge(String queue);
}
