package org.tus.shortlink.svc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.common.convention.result.Results;
import org.tus.shortlink.base.dto.req.RecycleBinRecoverReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinRemoveReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinSaveReqDTO;
import org.tus.shortlink.svc.service.RecycleBinService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shortlink/v1/trash")
public class TrashController {
    private final RecycleBinService recycleBinService;

    /**
     * SAVE short link to trash
     */
    @PostMapping("/save")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<Void> save(@RequestBody RecycleBinSaveReqDTO requestParam) {
        recycleBinService.saveRecycle(requestParam);
        return Results.success();
    }

//    /**
//     * LIST short links in trash with pagination
//     */
//    @GetMapping("/list")
//    public Result<Page<ShortLinkPageRespDTO>> list(ShortLinkRecycleBinPageReqDTO requestParam) {
//        return Results.success(recycleBinService.pageShortLink(requestParam));
//    }

    /**
     * RECOVER a short link from trash
     */
    @PutMapping("/recover")
    public Result<Void> recover(@RequestBody RecycleBinRecoverReqDTO requestParam) {
        recycleBinService.recoverRecycleBin(requestParam);
        return Results.success();
    }

    /**
     * DELETE a short link permanently
     */
    public Result<Void> delete(@RequestBody RecycleBinRemoveReqDTO requestParam) {
        recycleBinService.removeRecycle(requestParam);
        return Results.success();
    }

}
