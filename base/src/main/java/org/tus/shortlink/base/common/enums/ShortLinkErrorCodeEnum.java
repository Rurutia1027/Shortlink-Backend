package org.tus.shortlink.base.common.enums;

import org.tus.shortlink.base.common.convention.errorcode.IErrorCode;

public enum ShortLinkErrorCodeEnum implements IErrorCode {

    // ======================
    // Query / Access errors
    // ======================

    SHORT_LINK_NOT_FOUND("B001404", "Short link does not exist or has expired"),

    SHORT_LINK_DISABLED("B001403", "Short link is disabled"),

    SHORT_LINK_EXPIRED("B001410", "Short link has expired"),

    // ======================
    // Creation / Modification
    // ======================

    SHORT_LINK_ALREADY_EXISTS("B001409", "Short link already exists"),

    SHORT_LINK_CREATE_FAILED("B001500", "Failed to create short link"),

    SHORT_LINK_UPDATE_FAILED("B001501", "Failed to update short link"),

    SHORT_LINK_DELETE_FAILED("B001502", "Failed to delete short link"),

    // ======================
    // Validation / Constraint
    // ======================

    INVALID_ORIGIN_URL("B001400", "Invalid original URL"),

    INVALID_SHORT_URI("B001401", "Invalid short URI"),

    GROUP_NOT_FOUND("B001402", "Short link group does not exist"),

    // ======================
    // Security / Risk control
    // ======================

    FLOW_LIMIT_EXCEEDED("B001429", "Too many requests, please try again later");

    private final String code;
    private final String message;

    ShortLinkErrorCodeEnum(String code, String message) {
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
