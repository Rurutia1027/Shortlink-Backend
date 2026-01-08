package org.tus.shortlink.svc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.common.convention.result.Results;
import org.tus.shortlink.base.dto.req.RecycleBinSaveReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkRecycleBinPageReqDTO;
import org.tus.shortlink.svc.service.RecycleBinService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shortlink/v1/trash")
public class RecycleBinController {
    private final RecycleBinService recycleBinService;

    @PostMapping("/api/shortlink/v1/recycle/save")
    public Result<Void> saveRecycleBin(@RequestBody RecycleBinSaveReqDTO requestParam) {
        recycleBinService.saveRecycle(requestParam);
        return Results.success();
    }

    @GetMapping("/api/shortlink/v1/recycle/page")
    public Result pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        return Results.success(recycleBinService.pageShortLink(requestParam));
    }

    // TODO:
    @PostMapping("/api/shortlink")

}
