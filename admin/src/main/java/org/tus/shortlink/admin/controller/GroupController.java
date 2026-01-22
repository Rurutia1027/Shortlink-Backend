package org.tus.shortlink.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.common.convention.result.Results;
import org.tus.shortlink.base.dto.req.ShortLinkGroupSaveReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkGroupSortReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkGroupUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupRespDTO;
import org.tus.shortlink.admin.service.GroupService;

import java.util.List;

/**
 * Group controller for managing short link groups
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shortlink/admin/v1/group")
public class GroupController {
    private final GroupService groupService;

    /**
     * Create a new short link group
     */
    @PostMapping
    public Result<Void> save(@RequestBody ShortLinkGroupSaveReqDTO requestParam) {
        groupService.saveGroup(requestParam.getName());
        return Results.success();
    }

    /**
     * List user's short link groups
     */
    @GetMapping
    public Result<List<ShortLinkGroupRespDTO>> listGroup() {
        return Results.success(groupService.listGroup());
    }

    /**
     * Update short link group name
     */
    @PutMapping
    public Result<Void> updateGroup(@RequestBody ShortLinkGroupUpdateReqDTO requestParam) {
        groupService.updateGroup(requestParam);
        return Results.success();
    }

    /**
     * Delete short link group
     */
    @DeleteMapping
    public Result<Void> deleteGroup(@RequestParam String gid) {
        groupService.deleteGroup(gid);
        return Results.success();
    }

    /**
     * Sort short link groups
     */
    @PostMapping("/sort")
    public Result<Void> sortGroup(@RequestBody List<ShortLinkGroupSortReqDTO> requestParam) {
        groupService.sortGroup(requestParam);
        return Results.success();
    }
}
