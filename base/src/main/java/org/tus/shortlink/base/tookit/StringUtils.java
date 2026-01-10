package org.tus.shortlink.base.tookit;

import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.Charset;

public class StringUtils {
    public static final String UTF8 = "UTF-8";
    public static final Charset UTF8_CHARSET = Charset.forName(UTF8);


    /**
     * Determines whether the given {@link CharSequence} is not {@code null} and is not empty (has length greater
     * than zero). A {@link CharSequence} that contains only whitespace returns {@code true}.
     * {@code
     * StringUtils.hasLength(null) --> false
     * StringUtils.hasLength("") --> false
     * StringUtils.hasLength(" ") --> true
     * StringUtils.hasLength(" \n \t") --> true
     * StringUtils.hasLength("text") --> true
     * StringUtils.hasLength(" text ") --> true
     * }
     *
     * @param str the {@code CharSequence} to test for length. May be {@code null}.
     * @return {@code true} if {@code str} is not {@code null} and has length greater than {@code 0}; {@code false}
     * otherwise.
     */
    public static boolean hasLength(CharSequence str) {
        return (str != null && str.length() > 0);
    }

    /**
     * Determine whether the given {@link CharSequence} has text, that is, that it is not {@code null}, is not
     * empty (has length greater than {@code 0}), and contains at least one non-whitespace character.
     * {@code
     * StringUtils.hasText(null) --> false
     * StringUtils.hasText("") --> false
     * StringUtils.hasText(" ") --> false
     * StringUtils.hasText(" \n \t") --> false
     * StringUtils.hasText("text") --> true
     * StringUtils.hasText(" text ") --> true
     * }
     *
     * @param str the {@code CharSequence} to test for the presence of text. May be {@code null}.
     * @return {@code true} if {@code str} is not {@code null}, not empty, and contains at least one non-whitespace
     * character; {@code false} otherwise.
     */
    public static boolean hasText(CharSequence str) {
        // Verify not null and not empty
        if (!hasLength(str)) {
            return false;
        }

        // Look for non-whitespace characters
        int length = str.length();
        for (int i = 0; i < length; ++i) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }

        return false;
    }


    /**
     * Determines whether the given {@link String} has text, that is, that it is not {@code null}, not empty (has
     * length greater than zero), and contains at least one non-whitespace character.
     *
     * @param str the {@link String} to test for the presence of text. May be {@code null}.
     * @return {@code true} if {@code str} is not {@code null}, not empty, and contains at least one
     * non-whitespace character; {@code false} otherwise.
     */
    public static boolean hasText(String str) {
        return hasText((CharSequence) str);
    }

    public static boolean isEmpty(String str) {
        return !hasText((CharSequence) str);
    }

    /**
     * Joins the given array or variable number of String objects into a single string with components delimited by
     * the given separator.
     *
     * @param separator the text to insert between each string.
     * @param args      the strings to join
     * @return the joined string
     */
    public static String join(String separator, Object... args) {
        StringBuilder builder = new StringBuilder();
        boolean firstTime = true;
        for (Object arg : args) {
            if (!firstTime) {
                builder.append(separator);
            }
            builder.append(arg.toString());
            firstTime = false;
        }

        return builder.toString();
    }

    /**
     * Computes the SHA1 digest of the given text.
     *
     * @param input the input text
     * @return the SHA1 digest of the input text
     */
    public static String digest(String input) {
        return CodecUtils.encodeBase64URLSafeString(DigestUtils.sha1(input.getBytes(UTF8_CHARSET)));
    }
}
