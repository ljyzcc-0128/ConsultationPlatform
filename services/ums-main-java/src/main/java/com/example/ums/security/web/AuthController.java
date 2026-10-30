package com.example.ums.security.web;

import com.example.ums.security.AuthenticationService;
import com.example.ums.security.LoginRequest;
import com.example.ums.security.LoginResponse;
import com.example.ums.security.SecurityContextHelper;
import com.example.ums.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authService;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        try {
            return authService.login(req);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    /**
     * 当前登录用户信息（SSO 落地页凭 token 激活本地登录态时调用）。
     */
    @GetMapping("/me")
    public UserPrincipal me() {
        UserPrincipal principal = SecurityContextHelper.currentUser();
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return principal;
    }
}
