package org.tus.shortlink.svc.service;

import org.tus.shortlink.base.dto.req.ShortLinkGroupStatsReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkStatsReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsRespDTO;

public interface ShortLinkStatsService {
    ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam);

    ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam);

    //Page<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord
    // (ShortLinkStatsAccessRecordReqDTO requestParam);

    //Page<ShortLinkStatsAccessRecordRespDTO> groupShortLinkStatsAccessRecord
    // (ShortLinkGroupStatsAccessRecordReqDTO requestParam);
}
