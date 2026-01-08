package org.tus.shortlink.base.common.enums;

import org.tus.shortlink.base.common.convention.errorcode.IErrorCode;

public enum UserErrorCodeEnum implements IErrorCode {

    USER_NULL("B000200", "User record does not exist"),

    USER_NAME_EXIST("B000201", "Username already exists"),

    USER_EXIST("B000202", "User record already exists"),

    USER_SAVE_ERROR("B000203", "Failed to create user record");

    private final String code;
    private final String message;

    UserErrorCodeEnum(String code, String message) {
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
