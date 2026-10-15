package com.example.ums.security;

public record LoginRequest(String provider, String username, String credential) {}
