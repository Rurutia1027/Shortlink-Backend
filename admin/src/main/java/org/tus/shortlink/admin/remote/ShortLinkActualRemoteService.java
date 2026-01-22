package org.tus.shortlink.admin.remote;

import org.tus.common.domain.model.PageResponse;
import org.tus.shortlink.base.common.convention.result.Result;
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

import java.util.List;

/**
 * Remote service interface for calling shortlink service endpoints
 * Note: Implementation will use Istio Gateway for service communication
 * No Feign annotations are added as the communication mechanism is to be determined
 */
public interface ShortLinkActualRemoteService {

    /**
     * Create a short link
     *
     * @param requestParam create short link request parameters
     * @return short link create response
     */
    Result<ShortLinkCreateRespDTO> createShortLink(ShortLinkCreateReqDTO requestParam);

    /**
     * Batch create short links
     *
     * @param requestParam batch create short link request parameters
     * @return batch create short link response
     */
    Result<ShortLinkBatchCreateRespDTO> batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam);

    /**
     * Update a short link
     *
     * @param requestParam update short link request parameters
     */
    void updateShortLink(ShortLinkUpdateReqDTO requestParam);

    /**
     * Page query short links
     *
     * @param gid      group identifier
     * @param orderTag sort type
     * @param current  current page number
     * @param size     page size
     * @return page response with short link list
     */
    Result<PageResponse<ShortLinkPageRespDTO>> pageShortLink(String gid, String orderTag, Integer current, Integer size);

    /**
     * Query short link counts per group
     *
     * @param requestParam list of group identifiers
     * @return list of group short link counts
     */
    Result<List<ShortLinkGroupCountQueryRespDTO>> listGroupShortLinkCount(List<String> requestParam);

    /**
     * Get title by URL
     *
     * @param url target website URL
     * @return website title
     */
    Result<String> getTitleByUrl(String url);

    /**
     * Save short link to recycle bin
     *
     * @param requestParam request parameters
     */
    void saveRecycleBin(RecycleBinSaveReqDTO requestParam);

    /**
     * Page query recycle bin short links
     *
     * @param gidList list of group identifiers
     * @param current current page number
     * @param size    page size
     * @return page response with short link list
     */
    Result<PageResponse<ShortLinkPageRespDTO>> pageRecycleBinShortLink(List<String> gidList, Integer current, Integer size);

    /**
     * Recover short link from recycle bin
     *
     * @param requestParam recover short link request parameters
     */
    void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam);

    /**
     * Remove short link from recycle bin permanently
     *
     * @param requestParam remove short link request parameters
     */
    void removeRecycleBin(RecycleBinRemoveReqDTO requestParam);

    /**
     * Get single short link stats within specified time range
     *
     * @param fullShortUrl full short URL
     * @param gid          group identifier
     * @param enableStatus enable status
     * @param startDate    start date
     * @param endDate      end date
     * @return short link stats response
     */
    Result<ShortLinkStatsRespDTO> oneShortLinkStats(String fullShortUrl, String gid, Integer enableStatus,
                                                    String startDate, String endDate);

    /**
     * Get group short link stats within specified time range
     *
     * @param gid       group identifier
     * @param startDate start date
     * @param endDate   end date
     * @return group short link stats response
     */
    Result<ShortLinkStatsRespDTO> groupShortLinkStats(String gid, String startDate, String endDate);

    /**
     * Get short link stats access record within specified time range
     *
     * @param fullShortUrl full short URL
     * @param gid          group identifier
     * @param startDate    start date
     * @param endDate      end date
     * @param enableStatus enable status
     * @param current      current page number
     * @param size         page size
     * @return page response with access record list
     */
    Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(
            String fullShortUrl, String gid, String startDate, String endDate,
            Integer enableStatus, Integer current, Integer size);

    /**
     * Get group short link stats access record within specified time range
     *
     * @param gid       group identifier
     * @param startDate start date
     * @param endDate   end date
     * @param current   current page number
     * @param size      page size
     * @return page response with access record list
     */
    Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(
            String gid, String startDate, String endDate, Integer current, Integer size);
}
