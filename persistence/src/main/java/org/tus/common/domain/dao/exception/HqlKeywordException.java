package org.tus.common.domain.dao.exception;

/**
 * Exception indicating an issue when handling an input string to the builder which contained an HQL keyword
 */
public class HqlKeywordException extends RuntimeException {

    public HqlKeywordException(String message) {
        super(message);
    }

    public HqlKeywordException(String message, Throwable cause) {
        super(message, cause);
    }
}
