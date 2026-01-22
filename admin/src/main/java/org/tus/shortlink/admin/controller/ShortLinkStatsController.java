package org.tus.shortlink.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tus.common.domain.model.PageResponse;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkGroupStatsReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkStatsAccessRecordReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkStatsReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsRespDTO;
import org.tus.shortlink.admin.remote.ShortLinkActualRemoteService;

/**
 * Short link stats controller for admin module
 * Note: Stats implementation is kept as interface only, actual metrics should be handled by observability layer
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shortlink/admin/v1/stats")
public class ShortLinkStatsController {
    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    /**
     * Get single short link stats within specified time range
     */
    @GetMapping
    public Result<ShortLinkStatsRespDTO> shortLinkStats(ShortLinkStatsReqDTO requestParam) {
        return shortLinkActualRemoteService.oneShortLinkStats(
                requestParam.getFullShortUrl(),
                requestParam.getGid(),
                requestParam.getEnableStatus(),
                requestParam.getStartDate(),
                requestParam.getEndDate()
        );
    }

    /**
     * Get group short link stats within specified time range
     */
    @GetMapping("/group")
    public Result<ShortLinkStatsRespDTO> groupShortLinkStats(ShortLinkGroupStatsReqDTO requestParam) {
        return shortLinkActualRemoteService.groupShortLinkStats(
                requestParam.getGid(),
                requestParam.getStartDate(),
                requestParam.getEndDate()
        );
    }

    /**
     * Get single short link stats access record within specified time range
     */
    @GetMapping("/access-record")
    public Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(
            ShortLinkStatsAccessRecordReqDTO requestParam) {
        return shortLinkActualRemoteService.shortLinkStatsAccessRecord(
                requestParam.getFullShortUrl(),
                requestParam.getGid(),
                requestParam.getStartDate(),
                requestParam.getEndDate(),
                requestParam.getEnableStatus(),
                requestParam.getCurrent(),
                requestParam.getSize()
        );
    }

    /**
     * Get group short link stats access record within specified time range
     */
    @GetMapping("/access-record/group")
    public Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(
            ShortLinkGroupStatsAccessRecordReqDTO requestParam) {
        return shortLinkActualRemoteService.groupShortLinkStatsAccessRecord(
                requestParam.getGid(),
                requestParam.getStartDate(),
                requestParam.getEndDate(),
                requestParam.getCurrent(),
                requestParam.getSize()
        );
    }
}
