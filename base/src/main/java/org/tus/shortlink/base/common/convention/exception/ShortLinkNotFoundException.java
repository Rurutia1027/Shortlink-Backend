package org.tus.shortlink.base.common.convention.exception;

import org.tus.shortlink.base.common.convention.errorcode.BaseErrorCode;
import org.tus.shortlink.base.common.convention.errorcode.IErrorCode;
import org.tus.shortlink.base.common.enums.ShortLinkErrorCodeEnum;

public class ShortLinkNotFoundException extends AbstractException {

    public ShortLinkNotFoundException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public ShortLinkNotFoundException(String message) {
        this(message, null, ShortLinkErrorCodeEnum.SHORT_LINK_NOT_FOUND);
    }

    public ShortLinkNotFoundException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ShortLinkNotFoundException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }

    @Override
    public String toString() {
        return "ShortLinkNotFoundException{" +
                "code='" + errorCode + "'," +
                "message='" + errorMessage + "'" +
                '}';
    }
}
