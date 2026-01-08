package org.tus.shortlink.base.tookit;

import java.security.SecureRandom;

public final class RandomGenerator {

    private static final String CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generate a random group ID
     *
     * @return group ID
     */
    public static String generateRandom() {
        return generateRandom(6);
    }

    /**
     * Generate a random group ID of specified length
     *
     * @param length number of characters to generate
     * @return group ID
     */
    public static String generateRandom(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomIndex = RANDOM.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(randomIndex));
        }
        return sb.toString();
    }
}
