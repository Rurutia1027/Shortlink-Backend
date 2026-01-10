package org.tus.shortlink.base.tookit;

import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.jar.JarInputStream;

/**
 * Utility methods for manipulating byte/string converter
 */
public class CodecUtils {

    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Encoder BASE64_ENCODER_WITHPADDING = Base64.getEncoder();

    private static final Base64.Decoder BASE64_URLDECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder BASE64_URLENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Encoder BASE64_URLENCODER_WITHPADING = Base64.getUrlEncoder();

    /**
     * Encodes one byte array as string in base64
     *
     * @param src source byte array
     * @return encoded string
     */
    public static final String encodeBase64String(byte[] src) {
        return BASE64_ENCODER.encodeToString(src);
    }

    /**
     * Encodes one byte array as string in base64
     *
     * @param src     source byte array
     * @param padding if adding one padding character at the end
     * @return encoded string
     */
    public static final String encodeBase64String(byte[] src, boolean padding) {
        return padding ? BASE64_ENCODER_WITHPADDING.encodeToString(src) : BASE64_ENCODER.encodeToString(src);
    }

    /**
     * Encodes one string as byte array in base64
     *
     * @param src source string
     * @return encoded byte array
     */
    public static final byte[] encodeBase64(String src) {
        return BASE64_ENCODER.encode(src.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encodes one string as byte array in base64
     *
     * @param src     source string
     * @param padding if adding one padding character at the end
     * @return encoded byte array
     */
    public static final byte[] encodeBase64(String src, boolean padding) {
        return padding ? BASE64_ENCODER_WITHPADDING.encode(src.getBytes(StandardCharsets.UTF_8)) : BASE64_ENCODER.encode(src.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encodes one byte array as byte array in base64
     *
     * @param src source byte array
     * @return encoded string
     */
    public static final byte[] encodeBase64(byte[] src) {
        return BASE64_ENCODER.encode(src);
    }

    /**
     * Encodes one byte array as byte array in base64
     *
     * @param src     source byte array
     * @param padding if adding one padding character at the end
     * @return encoded string
     */
    public static final byte[] encodeBase64(byte[] src, boolean padding) {
        return padding ? BASE64_ENCODER_WITHPADDING.encode(src) : BASE64_ENCODER.encode(src);
    }

    /**
     * Encodes one byte array as {@link ByteBuffer} object in base64
     *
     * @param src source byte array
     * @return encoded string
     */
    public static final ByteBuffer encodeBase64(ByteBuffer src) {
        return BASE64_ENCODER.encode(src);
    }

    /**
     * Encodes one byte array as {@link ByteBuffer} object in base64
     *
     * @param src     source byte array
     * @param padding if adding one padding character at the end
     * @return encoded string
     */
    public static final ByteBuffer encodeBase64(ByteBuffer src, boolean padding) {
        return padding ? BASE64_ENCODER_WITHPADDING.encode(src) : BASE64_ENCODER.encode(src);
    }

    /**
     * Decodes one string in base64 to byte array
     *
     * @param src one string encoded in base64
     * @return encoded string
     */
    @SneakyThrows
    public static final byte[] decodeBase64(String src) {
        return BASE64_DECODER.decode(src.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes one byte array in base64 to byte array
     *
     * @param src one string encoded in base64
     * @return encoded string
     */
    @SneakyThrows
    public static final byte[] decodeBase64(byte[] src) {
        return BASE64_DECODER.decode(src);
    }

    /**
     * Decodes one {@link ByteBuffer} in base64 to {@link ByteBuffer}
     *
     * @param src one {@link ByteBuffer} Object encoded in base64
     * @return encoded string
     */

    @SneakyThrows
    public static final ByteBuffer decodeBase64(ByteBuffer src) {
        return BASE64_DECODER.decode(src);
    }

    ///////// URL Decoding/Encoding//////////////

    /**
     * Encodes one byte array to string using the
     * <a href="#url">URL and Filename safe</a> type base64 encoding scheme.
     *
     * @param src source byte array
     * @return encoded string in base64
     */
    public static final String encodeBase64URLSafeString(byte[] src) {
        return BASE64_URLENCODER.encodeToString(src);
    }

    /**
     * Encodes one byte array to string using the
     * <a href="#url">URL and Filename safe</a> type base64 encoding scheme.
     *
     * @param src     source byte array
     * @param padding if adding one padding character at the end
     * @return encoded string in base64
     */
    public static final String encodeBase64URLSafeString(byte[] src, boolean padding) {
        return padding ? BASE64_URLENCODER_WITHPADING.encodeToString(src) : BASE64_URLENCODER.encodeToString(src);
    }

    /**
     * Encodes one byte array to byte array using the
     * <a href="#url">URL and Filename safe</a> type base64 encoding scheme.
     *
     * @param src source byte array
     * @return encoded byte array in base64
     */
    public static final byte[] encodeBase64URL(byte[] src) {
        return BASE64_URLENCODER.encode(src);
    }

    /**
     * Encodes one byte array to byte array using the
     * <a href="#url">URL and Filename safe</a> type base64 encoding scheme.
     *
     * @param src     source byte array
     * @param padding if adding one padding character at the end
     * @return encoded byte array in base64
     */
    public static final byte[] encodeBase64URL(byte[] src, boolean padding) {
        return padding ? BASE64_URLENCODER_WITHPADING.encode(src) : BASE64_URLENCODER.encode(src);
    }

    /**
     * Decodes one string encoded using the
     * <a href="#url">URL and Filename safe</a> type base64 encoding scheme
     *
     * @param src encoded string in base64
     * @return decoded byte array
     */
    public static final byte[] decodeBase64URL(String src) {
        return BASE64_URLDECODER.decode(src.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes one byte array encoded using the
     * <a href="#url">URL and Filename safe</a> type base64 encoding scheme
     *
     * @param src encoded byte array in base64
     * @return decoded byte array
     */
    public static final byte[] decodeBase64URL(byte[] src) {
        return BASE64_URLDECODER.decode(src);
    }

    /**
     * Checks if byte array specified by {@code jarContent} is one jar file
     *
     * @param jarContent byte array of jar file
     * @return
     */
    @SneakyThrows
    public static final boolean isValidJar(byte[] jarContent) {
        JarInputStream jar = new JarInputStream(new ByteArrayInputStream(jarContent));
        return null != jar.getNextJarEntry() || null != jar.getManifest();
    }
}
