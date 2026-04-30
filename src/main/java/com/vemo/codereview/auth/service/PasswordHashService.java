package com.vemo.codereview.auth.service;

import com.vemo.codereview.common.exception.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Service;

@Service
public class PasswordHashService {

    public String sha256(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new DomainException("PASSWORD_HASH_FAILED", "Failed to hash password");
        }
    }

    public boolean matches(String plainText, String passwordHash) {
        if (plainText == null || passwordHash == null) {
            return false;
        }
        return sha256(plainText).equalsIgnoreCase(passwordHash);
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
