package org.linkweave.infrastructure.security;

import java.security.SecureRandom;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SecureRandomProvider {

    private final SecureRandom secureRandom = new SecureRandom();

    public void nextBytes(byte[] bytes) {
        secureRandom.nextBytes(bytes);
    }
}
