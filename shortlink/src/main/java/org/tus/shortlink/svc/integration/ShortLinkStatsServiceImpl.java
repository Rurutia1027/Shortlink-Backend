package org.tus.shortlink.svc.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tus.shortlink.base.dto.req.ShortLinkGroupStatsReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkStatsReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsRespDTO;
import org.tus.shortlink.svc.service.ShortLinkStatsService;

@Service
@RequiredArgsConstructor
public class ShortLinkStatsServiceImpl implements ShortLinkStatsService {
    @Override
    public ShortLinkStatsRespDTO oneShortLinkStats(ShortLinkStatsReqDTO requestParam) {
        // TODO
        return null;
    }

    @Override
    public ShortLinkStatsRespDTO groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam) {
        // TODO
        return null;
    }

//    @Override
//    public Page<ShortLinkStatsAccessRecordRespDTO> shortLinkStatsAccessRecord(
//            ShortLinkStatsAccessRecordReqDTO requestParam) {
//        // TODO
//        return null;
//    }
//
//    @Override
//    public Page<ShortLinkStatsAccessRecordRespDTO> groupShortLinkStatsAccessRecord(
//            ShortLinkGroupStatsAccessRecordReqDTO requestParam) {
//        // TODO
//        return null;
//    }
}
