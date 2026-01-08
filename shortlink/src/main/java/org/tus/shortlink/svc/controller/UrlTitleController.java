package org.tus.shortlink.svc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.common.convention.result.Results;
import org.tus.shortlink.svc.dto.UrlTitleReqDTO;
import org.tus.shortlink.svc.dto.UrlTitleRespDTO;
import org.tus.shortlink.svc.service.UrlTitleService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shortlink/v1/title")
public class UrlTitleController {

    private final UrlTitleService urlTitleService;

    @GetMapping
    public Result<UrlTitleRespDTO> getTitleByUrl(UrlTitleReqDTO requestParam) {
        String title = urlTitleService.getTitleByUrl(requestParam.getUrl());
        return Results.success(UrlTitleRespDTO.builder()
                .url(requestParam.getUrl())
                .title(title).build());
    }
}
