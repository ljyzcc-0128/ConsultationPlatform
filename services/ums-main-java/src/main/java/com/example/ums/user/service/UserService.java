package com.example.ums.user.service;

import com.example.ums.user.dto.UserCreateRequest;
import com.example.ums.user.dto.UserDto;
import com.example.ums.user.dto.UserUpdateRequest;

import java.util.List;

/** 用户管理服务。 */
public interface UserService {

    List<UserDto> list();

    UserDto create(UserCreateRequest request);

    UserDto update(String id, UserUpdateRequest request);

    void delete(String id);
}
