package org.tus.shortlink.admin.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tus.common.domain.model.PageResponse;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.common.convention.result.Results;
import org.tus.shortlink.base.dto.req.ShortLinkBatchCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkPageReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBaseInfoRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBatchCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.admin.remote.ShortLinkActualRemoteService;

import java.util.List;

/**
 * Short link controller for admin module
 * This controller proxies requests to the shortlink service
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shortlink/admin/v1")
public class ShortLinkController {
    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    /**
     * Create a short link
     */
    @PostMapping("/create")
    public Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return shortLinkActualRemoteService.createShortLink(requestParam);
    }

    /**
     * Batch create short links
     */
    @SneakyThrows
    @PostMapping("/create/batch")
    public void batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam,
                                     HttpServletResponse response) {
        Result<ShortLinkBatchCreateRespDTO> result = shortLinkActualRemoteService.batchCreateShortLink(requestParam);
        if (result.isSuccess() && result.getData() != null) {
            List<ShortLinkBaseInfoRespDTO> baseLinkInfos = result.getData().getBaseLinkInfos();
            // TODO: Implement EasyExcelWebUtil.write for batch export
            // EasyExcelWebUtil.write(response, "批量创建短链接-SaaS短链接系统", ShortLinkBaseInfoRespDTO.class, baseLinkInfos);
        }
    }

    /**
     * Update a short link
     */
    @PutMapping("/update")
    public Result<Void> updateShortLink(@RequestBody ShortLinkUpdateReqDTO requestParam) {
        shortLinkActualRemoteService.updateShortLink(requestParam);
        return Results.success();
    }

    /**
     * Page query short links
     */
    @GetMapping("/page")
    public Result<PageResponse<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam) {
        return shortLinkActualRemoteService.pageShortLink(
                requestParam.getGid(),
                requestParam.getOrderTag(),
                requestParam.getPageNo(),
                requestParam.getPageSize()
        );
    }
}
