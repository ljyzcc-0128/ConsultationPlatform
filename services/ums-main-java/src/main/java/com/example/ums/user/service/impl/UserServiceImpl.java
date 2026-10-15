package com.example.ums.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ums.admin.annotation.Audited;
import com.example.ums.user.dto.UserCreateRequest;
import com.example.ums.user.dto.UserDto;
import com.example.ums.user.dto.UserUpdateRequest;
import com.example.ums.user.mapper.UserMapper;
import com.example.ums.user.model.CpUser;
import com.example.ums.user.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** 用户管理实现。 */
@Service
public class UserServiceImpl implements UserService {

    private static final String DEFAULT_PROVIDER = "LOCAL";
    private static final String DEFAULT_STATUS = "ACTIVE";

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public UserServiceImpl(UserMapper userMapper, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<UserDto> list() {
        return userMapper.selectList(null).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Audited(action = "USER_CREATE", objectType = "cp_user")
    public UserDto create(UserCreateRequest request) {
        String provider = request.provider() != null ? request.provider() : DEFAULT_PROVIDER;
        Long count = userMapper.selectCount(new LambdaQueryWrapper<CpUser>()
                .eq(CpUser::getUsername, request.username())
                .eq(CpUser::getProvider, provider));
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "用户名已存在: " + request.username());
        }

        CpUser user = new CpUser();
        user.setUserId(UUID.randomUUID().toString());
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setEmail(request.email());
        user.setRoles(serializeRoles(request.roles()));
        user.setStatus(request.status() != null ? request.status() : DEFAULT_STATUS);
        user.setProvider(provider);
        userMapper.insert(user);

        return toDto(user);
    }

    @Override
    @Audited(action = "USER_UPDATE", objectType = "cp_user")
    public UserDto update(String id, UserUpdateRequest request) {
        CpUser user = userMapper.selectById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id);
        }

        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.roles() != null) {
            user.setRoles(serializeRoles(request.roles()));
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        userMapper.updateById(user);

        return toDto(userMapper.selectById(id));
    }

    @Override
    @Audited(action = "USER_DELETE", objectType = "cp_user")
    public void delete(String id) {
        userMapper.deleteById(id);
    }

    private UserDto toDto(CpUser user) {
        return new UserDto(
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                parseRoles(user.getRoles()),
                user.getStatus(),
                user.getProvider(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
    }

    private List<String> parseRoles(String rolesJson) {
        if (rolesJson == null || rolesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rolesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String serializeRoles(List<String> roles) {
        if (roles == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(roles);
        } catch (Exception e) {
            return "[]";
        }
    }
}
