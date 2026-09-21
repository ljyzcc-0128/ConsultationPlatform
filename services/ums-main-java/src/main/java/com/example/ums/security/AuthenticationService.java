package com.example.ums.security;

public interface AuthenticationService {
    LoginResponse login(LoginRequest req);
}
