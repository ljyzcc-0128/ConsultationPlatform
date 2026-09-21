package com.example.ums.user.dto;

import java.util.List;

public record UserUpdateRequest(
        String displayName,
        String email,
        List<String> roles,
        String status) {
}
