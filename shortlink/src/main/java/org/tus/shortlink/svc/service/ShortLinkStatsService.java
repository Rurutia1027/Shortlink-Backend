package org.tus.shortlink.svc.service;

import org.tus.common.domain.model.Page;
import org.tus.common.domain.model.PageResponse;
import org.tus.shortlink.base.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkGroupStatsReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkStatsAccessRecordReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkStatsReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsRespDTO;

public interface ShortLinkStatsService {
    ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam);

    ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam);

    PageResponse<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord
     (ShortLinkStatsAccessRecordReqDTO requestParam);

    PageResponse<ShortLinkStatsAccessRecordRespDTO> groupShortLinkStatsAccessRecord
     (ShortLinkGroupStatsAccessRecordReqDTO requestParam);
}
