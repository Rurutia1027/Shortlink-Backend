package org.tus.shortlink.admin.service;

import org.tus.common.domain.model.PageResponse;
import org.tus.shortlink.base.dto.req.ShortLinkRecycleBinPageReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;

/**
 * Recycle bin service interface for managing deleted short links
 */
public interface RecycleBinService {

    /**
     * Page query recycle bin short links
     *
     * @param requestParam request parameters
     * @return page response with short link list
     */
    PageResponse<ShortLinkPageRespDTO> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam);
}
