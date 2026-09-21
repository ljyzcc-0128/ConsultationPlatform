package com.example.ums.security;

public record AuthenticationRequest(
    String provider,
    String username,
    String credential
) {}
