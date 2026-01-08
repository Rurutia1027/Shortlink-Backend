package org.tus.shortlink.base.common.convention.errorcode;

public enum BaseErrorCode implements IErrorCode {

    // ========== Level-1 General Error Code: Client Errors ==========
    CLIENT_ERROR("A000001", "Client error"),

    // ========== Level-2 General Error Code: User Registration Errors ==========
    USER_REGISTER_ERROR("A000100", "User registration error"),
    USER_NAME_VERIFY_ERROR("A000110", "Username validation failed"),
    USER_NAME_EXIST_ERROR("A000111", "Username already exists"),
    USER_NAME_SENSITIVE_ERROR("A000112", "Username contains sensitive words"),
    USER_NAME_SPECIAL_CHARACTER_ERROR("A000113", "Username contains special characters"),
    PASSWORD_VERIFY_ERROR("A000120", "Password validation failed"),
    PASSWORD_SHORT_ERROR("A000121", "Password length is too short"),
    PHONE_VERIFY_ERROR("A000151", "Invalid phone number format"),

    // ========== Level-2 General Error Code: Missing Idempotency Token ==========
    IDEMPOTENT_TOKEN_NULL_ERROR("A000200", "Idempotency token is missing"),
    IDEMPOTENT_TOKEN_DELETE_ERROR("A000201", "Idempotency token has been used or expired"),

    // ========== Level-1 General Error Code: System Execution Errors ==========
    SERVICE_ERROR("B000001", "System execution error"),

    // ========== Level-2 General Error Code: System Timeout ==========
    SERVICE_TIMEOUT_ERROR("B000100", "System execution timeout"),

    // ========== Level-1 General Error Code: Remote Service Errors ==========
    REMOTE_ERROR("C000001", "Error occurred while calling remote service");

    private final String code;
    private final String message;

    BaseErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
