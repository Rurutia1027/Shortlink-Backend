package org.tus.shortlink.svc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tus.common.domain.model.PageResponse;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.common.convention.result.Results;
import org.tus.shortlink.base.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkGroupStatsReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkStatsAccessRecordReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkStatsReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsRespDTO;
import org.tus.shortlink.svc.service.ShortLinkStatsService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shortlink/v1/stats")
public class ShortLinkStatsController {
    private final ShortLinkStatsService shortLinkStatesService;

    /**
     * Stats for a single short link within a time range
     */
    @GetMapping
    public Result<ShortLinkStatsRespDTO> shortLinkStats(
            ShortLinkStatsReqDTO requestParam) {
        return Results.success(shortLinkStatesService.oneShortLinkStats(requestParam));
    }

    /**
     * Stats for a group of short links within a time range
     */
    @GetMapping("/group")
    public Result<ShortLinkStatsRespDTO> groupShortLinkStats(
            ShortLinkGroupStatsReqDTO requestParam) {
        return Results.success(shortLinkStatesService.groupShortLinkStats(requestParam));
    }

    /**
     * Access records for a single short link
     */
    @GetMapping("/access")
    public Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(
            ShortLinkStatsAccessRecordReqDTO requestParam) {
        return Results.success(shortLinkStatesService.shortLinkStatsAccessRecord(requestParam));
    }

    /**
     * Access records for a group of short links
     */
    @GetMapping("/group/access")
    public Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(
            ShortLinkGroupStatsAccessRecordReqDTO requestParam) {
        return Results.success(shortLinkStatesService.groupShortLinkStatsAccessRecord(requestParam));
    }
}
