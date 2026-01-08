package org.tus.shortlink.svc.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.common.convention.result.Results;

/**
 * Short link error controller
 */

@RestController
@RequestMapping("/api/errors")
public class ShortLinkErrorApiController {
    /**
     * Short link not found
     */
    @GetMapping("/shortlink-not-found")
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> shortLinkNotFound() {
        return Results.failure(
                "SHORT_LINK_NOT_FOUND",
                "The short link does not exist or has expired"
        );
    }
}
