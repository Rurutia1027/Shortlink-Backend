package org.tus.shortlink.base.common.convention.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 5679018624309023727L;

    /**
     * Success Code
     */
    public static final String SUCCESS_CODE = "0";

    /**
     * Response Code
     */
    private String code;

    /**
     * Response Message
     */
    private String message;

    /**
     * Response Data
     */
    private T data;

    /**
     * Request ID
     */
    private String requestId;

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }
}
