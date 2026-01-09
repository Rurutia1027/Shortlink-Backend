package org.tus.shortlink.svc.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.tus.shortlink.base.dto.req.ShortLinkBatchCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkPageReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBatchCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupCountQueryRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;

import java.io.IOException;
import java.util.List;

public interface ShortLinkService {
    void restoreUrl(String shortUrl, HttpServletRequest httpRequest,
                    HttpServletResponse httpResponse) throws IOException;

    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam);

    ShortLinkCreateRespDTO createShortLinkByLock(ShortLinkCreateReqDTO requestParam);

    ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam);

    void updateShortLink(ShortLinkUpdateReqDTO requestParam);

    Page<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam);

    List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> groupIds);
}
