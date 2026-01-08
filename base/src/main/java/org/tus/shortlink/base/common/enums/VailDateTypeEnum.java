package org.tus.shortlink.base.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum VailDateTypeEnum {

    /**
     * Permanent Validate Date Flag
     */
    PERMANENT(0),

    /**
     * Custom Validate Date Flag
     */
    CUSTOM(1);

    @Getter
    private final int type;
}
