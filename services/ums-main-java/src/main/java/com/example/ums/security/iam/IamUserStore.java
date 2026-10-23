package com.example.ums.security.iam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.user.mapper.UserMapper;
import com.example.ums.user.model.CpUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * IAM 外部用户本地账户管理：按 provider=IAM + externalId 查找或自动创建 cp_user。
 * 供 IAM API 调用模式（IamAuthProvider）与晶彩单点登录（SsoService）共用。
 */
@Component
public class IamUserStore {

    private static final Logger log = LoggerFactory.getLogger(IamUserStore.class);

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IamUserStore(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 按 external_id + provider=IAM 查找 cp_user；
     * 未找到且 autoProvision=true 时自动创建，autoProvision=false 时抛出异常。
     * 已存在用户同步 displayName/email/roles 的变更。
     */
    public CpUser findOrCreate(String externalId, String username, String displayName,
                               String email, List<String> roles,
                               boolean autoProvision, List<String> defaultRoles) {
        LambdaQueryWrapper<CpUser> qw = new LambdaQueryWrapper<>();
        qw.eq(CpUser::getExternalId, externalId)
          .eq(CpUser::getProvider, "IAM");
        CpUser user = userMapper.selectOne(qw);

        if (user != null) {
            boolean changed = false;
            if (displayName != null && !displayName.equals(user.getDisplayName())) {
                user.setDisplayName(displayName);
                changed = true;
            }
            if (email != null && !email.equals(user.getEmail())) {
                user.setEmail(email);
                changed = true;
            }
            String rolesJson = serializeRoles(roles, defaultRoles);
            if (rolesJson != null && !rolesJson.equals(user.getRoles())) {
                user.setRoles(rolesJson);
                changed = true;
            }
            if (changed) {
                userMapper.updateById(user);
                log.info("IAM user info updated: externalId={}", externalId);
            }
            return user;
        }

        if (!autoProvision) {
            throw new IllegalArgumentException("IAM 用户未注册，请联系管理员开通账号");
        }

        user = new CpUser();
        user.setUserId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setRoles(serializeRoles(roles, defaultRoles));
        user.setStatus("ACTIVE");
        user.setProvider("IAM");
        user.setExternalId(externalId);
        try {
            userMapper.insert(user);
            log.info("IAM auto-provisioned user: externalId={} username={}", externalId, username);
            return user;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发回调（双击/重放）时另一请求已建户：按唯一键重查返回
            CpUser existing = userMapper.selectOne(qw);
            if (existing != null) {
                log.info("IAM 用户已被并发创建，复用: externalId={}", externalId);
                return existing;
            }
            // 撞的是 uk_username_provider（同 provider 内 username 重复）：改用 externalId 兜底
            log.warn("IAM 用户 username 冲突，改用 externalId 作为 username: {} -> {}", username, externalId);
            user.setUsername(externalId);
            user.setUserId(UUID.randomUUID().toString());
            userMapper.insert(user);
            return user;
        }
    }

    /** roles 为空时回退默认角色，序列化为 JSON 数组字符串 */
    public String serializeRoles(List<String> roles, List<String> defaultRoles) {
        if (roles == null || roles.isEmpty()) {
            roles = defaultRoles;
        }
        if (roles == null || roles.isEmpty()) {
            roles = List.of("USER");
        }
        try {
            return objectMapper.writeValueAsString(roles);
        } catch (Exception e) {
            return "[\"USER\"]";
        }
    }

    /** 解析 cp_user.roles JSON；异常时回退 USER */
    public List<String> parseRoles(String rolesJson) {
        if (rolesJson == null || rolesJson.isBlank()) {
            return List.of("USER");
        }
        try {
            return objectMapper.readValue(rolesJson,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return List.of("USER");
        }
    }
}
