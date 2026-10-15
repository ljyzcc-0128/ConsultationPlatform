package com.example.ums.security;

import java.io.Serializable;
import java.util.List;

public record UserPrincipal(
    String userId,
    String username,
    String displayName,
    List<String> roles,
    String provider,
    String externalId
) implements Serializable {}
