package com.example.ums.push.jingcai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.user.mapper.UserMapper;
import com.example.ums.user.model.CpUser;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 晶彩日报接收人解析器。
 *
 * <p>名单来源（去重合并）：
 * <ol>
 *   <li>自动名单：cp_user 中 provider=IAM 且 ACTIVE 的用户，其 external_id 即
 *       IAM uid（工号）= 钉钉 userid。用户 SSO 登录时由 IamUserStore 落库，
 *       因此「登录即自动加入接收名单」，无需手工维护；</li>
 *   <li>手动名单：环境变量 UMS_JINGCAI_RECEIVERS 配置的额外钉钉 userid
 *       （用于覆盖未登录但需要接收的人，如本地账号）。</li>
 * </ol>
 *
 * <p>自动名单可通过 ums.jingcai.auto-subscribe / UMS_JINGCAI_AUTO_SUBSCRIBE=false 关闭。
 */
@Component
@RequiredArgsConstructor
public class JingcaiReceiverResolver {

    private final JingcaiProperties props;
    private final UserMapper userMapper;

    /** 解析本次推送的接收人钉钉 userid 列表（手动名单在前，自动名单随后，去重保序）。 */
    public List<String> resolve() {
        LinkedHashSet<String> ids = new LinkedHashSet<>(props.receiverList());
        if (props.isAutoSubscribe()) {
            LambdaQueryWrapper<CpUser> qw = new LambdaQueryWrapper<>();
            qw.eq(CpUser::getProvider, "IAM")
              .eq(CpUser::getStatus, "ACTIVE");
            for (CpUser user : userMapper.selectList(qw)) {
                String externalId = user.getExternalId();
                if (externalId != null && !externalId.isBlank()) {
                    ids.add(externalId.trim());
                }
            }
        }
        return new ArrayList<>(ids);
    }
}
