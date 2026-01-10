package org.tus.shortlink.base.common.convention.exception;

import lombok.Getter;
import org.tus.shortlink.base.common.convention.errorcode.IErrorCode;
import org.tus.shortlink.base.tookit.StringUtils;

import java.util.Optional;


@Getter
public abstract class AbstractException extends RuntimeException {

    public final String errorCode;

    public final String errorMessage;

    public AbstractException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable);
        this.errorCode = errorCode.code();
        this.errorMessage = Optional.ofNullable(StringUtils.hasLength(message) ? message :
                null).orElse(errorCode.message());
    }
}