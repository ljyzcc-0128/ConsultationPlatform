package com.example.ums.security;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuthenticationServiceImpl implements AuthenticationService {
    private final Map<String, AuthenticationProvider> providers;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthenticationServiceImpl(List<AuthenticationProvider> providers, JwtTokenProvider jwtTokenProvider) {
        this.providers = providers.stream().collect(Collectors.toMap(AuthenticationProvider::providerId, p -> p));
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public LoginResponse login(LoginRequest req) {
        String providerId = req.provider() != null ? req.provider() : "LOCAL";
        AuthenticationProvider provider = providers.get(providerId);
        if (provider == null) throw new IllegalArgumentException("不支持的认证方式: " + providerId);
        AuthenticationRequest authReq = new AuthenticationRequest(req.provider(), req.username(), req.credential());
        UserPrincipal principal = provider.authenticate(authReq);
        String token = jwtTokenProvider.generate(principal);
        return new LoginResponse(token, principal);
    }
}
