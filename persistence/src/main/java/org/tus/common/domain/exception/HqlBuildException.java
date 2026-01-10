package org.tus.common.domain.exception;

/**
 * Exception indicating an issue when calling the build function of HqlQueryBuilder
 */
public class HqlBuildException extends RuntimeException {

    public HqlBuildException(String message) {
        super(message);
    }

    public HqlBuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
