package com.vemo.codereview.auth.service;

import com.vemo.codereview.auth.context.UserContextHolder;
import com.vemo.codereview.auth.model.AuthSession;
import com.vemo.codereview.common.exception.DomainException;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public AuthSession requireCurrentUser() {
        AuthSession session = UserContextHolder.get();
        if (session == null) {
            throw new DomainException("AUTH_UNAUTHORIZED", "Current user is missing");
        }
        return session;
    }

    public Long requireCurrentUserId() {
        return requireCurrentUser().getUserId();
    }

    public String requireCurrentUserDisplayName() {
        return requireCurrentUser().getDisplayName();
    }

    public boolean isAdmin() {
        AuthSession session = UserContextHolder.get();
        return session != null && "ADMIN".equalsIgnoreCase(session.getRole());
    }
}
