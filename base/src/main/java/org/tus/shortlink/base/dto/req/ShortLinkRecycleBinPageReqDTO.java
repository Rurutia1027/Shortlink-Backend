package org.tus.shortlink.base.dto.req;

import lombok.Data;

import java.util.List;

@Data
public class ShortLinkRecycleBinPageReqDTO {

    /**
     * List of group identifiers
     */
    private List<String> gidList;

    private int pageNum = 1;

    private int pageSize = 20;
}
