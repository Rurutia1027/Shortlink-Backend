package org.tus.shortlink.admin.remote.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tus.common.domain.model.PageResponse;
import org.tus.shortlink.base.common.convention.errorcode.BaseErrorCode;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.common.convention.result.Results;
import org.tus.shortlink.base.dto.req.RecycleBinRecoverReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinRemoveReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinSaveReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkBatchCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBatchCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupCountQueryRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsRespDTO;
import org.tus.shortlink.admin.remote.ShortLinkActualRemoteService;

import java.util.ArrayList;
import java.util.List;

/**
 * Temporary implementation of ShortLinkActualRemoteService
 * TODO: Implement actual HTTP calls to shortlink service via Istio Gateway
 */
@Slf4j
@Service
public class ShortLinkActualRemoteServiceImpl implements ShortLinkActualRemoteService {

    @Override
    public Result<ShortLinkCreateRespDTO> createShortLink(ShortLinkCreateReqDTO requestParam) {
        log.warn("ShortLinkActualRemoteService.createShortLink not implemented yet");
        // TODO: Implement HTTP call to shortlink service
        return new Result<ShortLinkCreateRespDTO>()
                .setCode(BaseErrorCode.REMOTE_ERROR.code())
                .setMessage("Not implemented yet");
    }

    @Override
    public Result<ShortLinkBatchCreateRespDTO> batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        log.warn("ShortLinkActualRemoteService.batchCreateShortLink not implemented yet");
        // TODO: Implement HTTP call to shortlink service
        return new Result<ShortLinkBatchCreateRespDTO>()
                .setCode(BaseErrorCode.REMOTE_ERROR.code())
                .setMessage("Not implemented yet");
    }

    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        log.warn("ShortLinkActualRemoteService.updateShortLink not implemented yet");
        // TODO: Implement HTTP call to shortlink service
    }

    @Override
    public Result<PageResponse<ShortLinkPageRespDTO>> pageShortLink(String gid, String orderTag, Integer current, Integer size) {
        log.warn("ShortLinkActualRemoteService.pageShortLink not implemented yet");
        // TODO: Implement HTTP call to shortlink service
        PageResponse<ShortLinkPageRespDTO> emptyPage = new PageResponse<>();
        emptyPage.setStart(0);
        emptyPage.setPageSize(size != null ? size : 10);
        emptyPage.setTotal(0);
        emptyPage.setElements(new ArrayList<>());
        return Results.success(emptyPage);
    }

    @Override
    public Result<List<ShortLinkGroupCountQueryRespDTO>> listGroupShortLinkCount(List<String> requestParam) {
        log.warn("ShortLinkActualRemoteService.listGroupShortLinkCount not implemented yet");
        // TODO: Implement HTTP call to shortlink service
        return Results.success(new ArrayList<>());
    }

    @Override
    public Result<String> getTitleByUrl(String url) {
        log.warn("ShortLinkActualRemoteService.getTitleByUrl not implemented yet");
        // TODO: Implement HTTP call to shortlink service
        return new Result<String>()
                .setCode(BaseErrorCode.REMOTE_ERROR.code())
                .setMessage("Not implemented yet");
    }

    @Override
    public void saveRecycleBin(RecycleBinSaveReqDTO requestParam) {
        log.warn("ShortLinkActualRemoteService.saveRecycleBin not implemented yet");
        // TODO: Implement HTTP call to shortlink service
    }

    @Override
    public Result<PageResponse<ShortLinkPageRespDTO>> pageRecycleBinShortLink(List<String> gidList, Integer current, Integer size) {
        log.warn("ShortLinkActualRemoteService.pageRecycleBinShortLink not implemented yet");
        // TODO: Implement HTTP call to shortlink service
        PageResponse<ShortLinkPageRespDTO> emptyPage = new PageResponse<>();
        emptyPage.setStart(0);
        emptyPage.setPageSize(size != null ? size : 10);
        emptyPage.setTotal(0);
        emptyPage.setElements(new ArrayList<>());
        return Results.success(emptyPage);
    }

    @Override
    public void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        log.warn("ShortLinkActualRemoteService.recoverRecycleBin not implemented yet");
        // TODO: Implement HTTP call to shortlink service
    }

    @Override
    public void removeRecycleBin(RecycleBinRemoveReqDTO requestParam) {
        log.warn("ShortLinkActualRemoteService.removeRecycleBin not implemented yet");
        // TODO: Implement HTTP call to shortlink service
    }

    @Override
    public Result<ShortLinkStatsRespDTO> oneShortLinkStats(String fullShortUrl, String gid, Integer enableStatus,
                                                           String startDate, String endDate) {
        log.warn("ShortLinkActualRemoteService.oneShortLinkStats not implemented yet");
        // TODO: Implement HTTP call to shortlink service
        return new Result<ShortLinkStatsRespDTO>()
                .setCode(BaseErrorCode.REMOTE_ERROR.code())
                .setMessage("Not implemented yet");
    }

    @Override
    public Result<ShortLinkStatsRespDTO> groupShortLinkStats(String gid, String startDate, String endDate) {
        log.warn("ShortLinkActualRemoteService.groupShortLinkStats not implemented yet");
        // TODO: Implement HTTP call to shortlink service
        return new Result<ShortLinkStatsRespDTO>()
                .setCode(BaseErrorCode.REMOTE_ERROR.code())
                .setMessage("Not implemented yet");
    }

    @Override
    public Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(
            String fullShortUrl, String gid, String startDate, String endDate,
            Integer enableStatus, Integer current, Integer size) {
        log.warn("ShortLinkActualRemoteService.shortLinkStatsAccessRecord not implemented yet");
        // TODO: Implement HTTP call to shortlink service
        PageResponse<ShortLinkStatsAccessRecordRespDTO> emptyPage = new PageResponse<>();
        emptyPage.setStart(0);
        emptyPage.setPageSize(size != null ? size : 10);
        emptyPage.setTotal(0);
        emptyPage.setElements(new ArrayList<>());
        return Results.success(emptyPage);
    }

    @Override
    public Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(
            String gid, String startDate, String endDate, Integer current, Integer size) {
        log.warn("ShortLinkActualRemoteService.groupShortLinkStatsAccessRecord not implemented yet");
        // TODO: Implement HTTP call to shortlink service
        PageResponse<ShortLinkStatsAccessRecordRespDTO> emptyPage = new PageResponse<>();
        emptyPage.setStart(0);
        emptyPage.setPageSize(size != null ? size : 10);
        emptyPage.setTotal(0);
        emptyPage.setElements(new ArrayList<>());
        return Results.success(emptyPage);
    }
}
