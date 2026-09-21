package com.example.ums.security.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.security.AuthenticationProvider;
import com.example.ums.security.AuthenticationRequest;
import com.example.ums.security.UserPrincipal;
import com.example.ums.user.mapper.UserMapper;
import com.example.ums.user.model.CpUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LocalAuthProvider implements AuthenticationProvider {
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalAuthProvider(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public String providerId() { return "LOCAL"; }

    @Override
    public UserPrincipal authenticate(AuthenticationRequest req) {
        LambdaQueryWrapper<CpUser> qw = new LambdaQueryWrapper<>();
        qw.eq(CpUser::getUsername, req.username())
          .eq(CpUser::getProvider, "LOCAL");
        CpUser user = userMapper.selectOne(qw);
        if (user == null) throw new BadCredentialsException("用户不存在");
        if (!"ACTIVE".equals(user.getStatus())) throw new BadCredentialsException("用户已禁用");
        List<String> roles = parseRoles(user.getRoles());
        return new UserPrincipal(
            user.getUserId(), user.getUsername(), user.getDisplayName(),
            roles, user.getProvider(), user.getExternalId()
        );
    }

    private List<String> parseRoles(String rolesJson) {
        try {
            return objectMapper.readValue(rolesJson, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of("USER");
        }
    }
}
