package com.example.ums.user.web;

import com.example.ums.user.dto.UserCreateRequest;
import com.example.ums.user.dto.UserDto;
import com.example.ums.user.dto.UserUpdateRequest;
import com.example.ums.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;

/** 用户管理接口。 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 用户列表。 */
    @GetMapping
    public List<UserDto> list() {
        return userService.list();
    }

    /** 创建用户。 */
    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody UserCreateRequest request) {
        UserDto dto = userService.create(request);
        return ResponseEntity.status(CREATED).body(dto);
    }

    /** 更新用户。 */
    @PutMapping("/{id}")
    public UserDto update(@PathVariable String id, @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }

    /** 删除用户。 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        userService.delete(id);
    }
}
