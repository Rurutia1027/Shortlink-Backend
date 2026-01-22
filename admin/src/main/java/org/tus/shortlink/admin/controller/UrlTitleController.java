package org.tus.shortlink.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.admin.remote.ShortLinkActualRemoteService;

/**
 * URL title controller for admin module
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shortlink/admin/v1/title")
public class UrlTitleController {
    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    /**
     * Get website title by URL
     */
    @GetMapping
    public Result<String> getTitleByUrl(@RequestParam("url") String url) {
        return shortLinkActualRemoteService.getTitleByUrl(url);
    }
}
