package org.tus.shortlink.base.tookit;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.Charset;
import java.util.regex.Pattern;

public class StringUtils {
    public static final String UTF8 = "UTF-8";
    public static final Charset UTF8_CHARSET = Charset.forName(UTF8);


    /**
     * Define which characters are invalid for name.
     * Invalid characters: ; / ? : @ = & " < > # % { } | \ ' ^ ~ [ ] ` <blank>
     */
    private static final Pattern INVALID_NAME_PATTERN = Pattern.compile(".*[;/?:@=&\\\"<>#%{}|\\\\'^~\\[\\]`\\s\u0000].*");
    /**
     * Define which characters are invalid for displayName.
     * Invalid characters: ; / ? : @ = & " < > # % { } | \ ' ^ ~ [ ] ` <blank>
     */
    private static final Pattern INVALID_DISPLAY_NAME_PATTERN = Pattern.compile(".*[;/?:@=&\\\"<>#%{}|\\\\'^~\\[\\]`\u0000].*");

    /**
     * Define which characters are invalid for name
     * Compare to INVALID_IDM_NAME_PATTERN:
     *   @ ' is allowed as we use email as username in some case
     */
    private static final Pattern INVALID_USER_NAME_PATTERN = Pattern.compile(".*[;/?:=&\\\"<>#%{}|\\^~\\[\\]`\\s\u0000].*");

    /**
     * Define which characters are invalid for name
     * Compare to INVALID_USER_NAME_PATTERN:
     *   space : is allowed
     */
    private static final Pattern INVALID_USER_DISPLAY_NAME_PATTERN = Pattern.compile(".*[;/?=&\\\"<>#%{}|\\^~\\[\\]`\u0000].*");

    public static final String UTC_DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";



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

    public static boolean isNameValid(String name) {
        return !INVALID_NAME_PATTERN.matcher(name).matches();
    }

    public static boolean isDisplayNameValid(String name) {
        return !INVALID_DISPLAY_NAME_PATTERN.matcher(name).matches();
    }

    public static boolean isValidUserName(String username) {
        return !INVALID_USER_NAME_PATTERN.matcher(username).matches();
    }

    public static boolean isValidUserDisplayName(String username) {
        return !INVALID_USER_DISPLAY_NAME_PATTERN.matcher(username).matches();
    }

    public static String getRemoteAddr(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "";
    }
}
