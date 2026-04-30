package com.vemo.codereview.auth.context;

import com.vemo.codereview.auth.model.AuthSession;

public final class UserContextHolder {

    private static final ThreadLocal<AuthSession> HOLDER = new ThreadLocal<AuthSession>();

    private UserContextHolder() {
    }

    public static void set(AuthSession session) {
        HOLDER.set(session);
    }

    public static AuthSession get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
