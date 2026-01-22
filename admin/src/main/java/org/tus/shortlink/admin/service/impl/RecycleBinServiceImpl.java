package org.tus.shortlink.admin.service.impl;

import org.springframework.stereotype.Service;
import org.tus.common.domain.model.PageResponse;
import org.tus.shortlink.admin.service.RecycleBinService;
import org.tus.shortlink.base.dto.req.ShortLinkRecycleBinPageReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;

@Service
public class RecycleBinServiceImpl implements RecycleBinService {
    @Override
    public PageResponse<ShortLinkPageRespDTO> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        return null;
    }
}
