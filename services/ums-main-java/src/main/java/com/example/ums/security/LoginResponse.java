package com.example.ums.security;

public record LoginResponse(String token, UserPrincipal user) {}
