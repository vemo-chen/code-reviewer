package com.vemo.codereview.auth.service;

import com.vemo.codereview.auth.model.AuthSession;
import com.vemo.codereview.user.entity.UserEntity;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class AuthTokenService {

    private final ConcurrentHashMap<String, AuthSession> sessions = new ConcurrentHashMap<String, AuthSession>();

    public String createToken(UserEntity user) {
        String token = "cr_" + UUID.randomUUID().toString().replace("-", "");
        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setUsername(user.getUsername());
        session.setDisplayName(user.getDisplayName());
        session.setRole(user.getRole());
        sessions.put(token, session);
        return token;
    }

    public AuthSession getSession(String token) {
        return token == null ? null : sessions.get(token);
    }

    public void removeToken(String token) {
        if (token == null) {
            return;
        }
        sessions.remove(token);
    }
}
