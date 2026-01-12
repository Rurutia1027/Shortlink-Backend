package org.tus.shortlink.svc.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tus.common.domain.model.PageResponse;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.common.convention.result.Results;
import org.tus.shortlink.base.dto.req.ShortLinkBatchCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkPageReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBatchCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupCountQueryRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.svc.service.ShortLinkService;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shortlink/v1")
public class ShortLinkController {
    private final ShortLinkService shortLinkService;

    /**
     * Redirect short link to original URL
     */
    @GetMapping("/{shortUri}")
    public void redirectToOriginal(@PathVariable String shortUrl,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) throws IOException {
        shortLinkService.restoreUrl(shortUrl, httpRequest, httpResponse);
    }

    /**
     * Create a new short link
     */
    @PostMapping("/links/create")
    // TODO rate-limit later redesign and impl
    public Result<ShortLinkCreateRespDTO> create(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return Results.success(shortLinkService.createShortLink(requestParam));
    }

    /**
     * Create short link using distributed lock
     */
    @PostMapping("/links/create/lock")
    public Result<ShortLinkCreateRespDTO> createWithLock(@RequestBody ShortLinkCreateReqDTO requestParam) {
        return Results.success(shortLinkService.createShortLinkByLock(requestParam));
    }

    /**
     * Batch create short links
     */
    @PostMapping("/links/create/batch")
    public Result<ShortLinkBatchCreateRespDTO> batchCreate(@RequestBody ShortLinkBatchCreateReqDTO requestParam) {
        return Results.success(shortLinkService.batchCreateShortLink(requestParam));
    }

    /**
     * Update an existing short link
     */
    @PutMapping("/links/update")
    public Result<Void> update(@RequestBody ShortLinkUpdateReqDTO requestParam) {
        shortLinkService.updateShortLink(requestParam);
        return Results.success();
    }

    /**
     * Paginate through short links
     */
    @PostMapping("/links/page")
    public Result<PageResponse<ShortLinkPageRespDTO>> list( @RequestBody ShortLinkPageReqDTO requestParam) {
        return Results.success(shortLinkService.pageShortLink(requestParam));
    }

    /**
     * List short link counts per group
     */
    @GetMapping("/links/gcount")
    public Result<List<ShortLinkGroupCountQueryRespDTO>> listGroupCounts(@RequestParam List<String> groupIds) {
        return Results.success(shortLinkService.listGroupShortLinkCount(groupIds));
    }
}
