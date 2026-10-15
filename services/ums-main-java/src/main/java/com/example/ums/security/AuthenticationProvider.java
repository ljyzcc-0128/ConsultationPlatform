package com.example.ums.security;

import org.springframework.security.core.AuthenticationException;

public interface AuthenticationProvider {
    String providerId();
    UserPrincipal authenticate(AuthenticationRequest req) throws AuthenticationException;
}
