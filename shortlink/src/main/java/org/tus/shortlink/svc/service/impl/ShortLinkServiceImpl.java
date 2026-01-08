package org.tus.shortlink.svc.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tus.shortlink.base.dto.req.ShortLinkBatchCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkPageReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBatchCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupCountQueryRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.svc.service.ShortLinkService;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ShortLinkServiceImpl implements ShortLinkService {
    @Override
    public void restoreUrl(String shortUri, HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) {
        // TODO
    }

    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        // TODO
        return null;
    }

    @Override
    public ShortLinkCreateRespDTO createShortLinkByLock(ShortLinkCreateReqDTO requestParam) {
        // TODO
        return null;
    }

    @Override
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        // TODO
        return null;
    }

    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        // TODO
    }

    @Override
    public ShortLinkPageRespDTO pageShortLink(ShortLinkPageReqDTO requestParam) {
        // TODO
        return null;
    }

    @Override
    public List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> groupIds) {
        // TODO
        return List.of();
    }
}
