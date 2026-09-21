package com.example.ums.user.dto;

import java.util.List;

public record UserDto(
        String id,
        String username,
        String displayName,
        String email,
        List<String> roles,
        String status,
        String provider,
        String createdAt) {
}
