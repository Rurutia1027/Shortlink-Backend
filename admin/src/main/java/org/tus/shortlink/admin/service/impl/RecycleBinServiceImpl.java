package org.tus.shortlink.admin.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tus.common.domain.dao.HqlQueryBuilder;
import org.tus.common.domain.model.PageResponse;
import org.tus.common.domain.persistence.QueryService;
import org.tus.shortlink.admin.entity.Group;
import org.tus.shortlink.admin.remote.ShortLinkActualRemoteService;
import org.tus.shortlink.admin.service.RecycleBinService;
import org.tus.shortlink.base.common.convention.exception.ServiceException;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.dto.req.ShortLinkRecycleBinPageReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Recycle bin service implementation using QueryService + HqlQueryBuilder
 * Migrated from Mybatis-based implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {
    private final QueryService queryService;
    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    @Override
    public PageResponse<ShortLinkPageRespDTO> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        // TODO: Get username from UserContext
        // String username = UserContext.getUsername();
        // For now, throw exception to indicate UserContext is needed
        throw new ServiceException("UserContext not implemented. Please implement " +
                "UserContext.getUsername()");
    }

    /**
     * Page query recycle bin short links for a specific username
     * This is a helper method that can be called once UserContext is implemented
     *
     * @param username     username
     * @param requestParam request parameters
     * @return page response with short link list
     */
    public PageResponse<ShortLinkPageRespDTO> pageRecycleBinShortLink(String username,
                                                                      ShortLinkRecycleBinPageReqDTO requestParam) {
        if (requestParam == null) {
            throw new IllegalArgumentException("requestParam must not be null");
        }

        // 1. Query user's groups (not deleted)
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(Group.class, "g")
                .select("g")
                .eq("g.username", username)
                .and()
                .isNull("g.deleted") // delFlag = 0 -> deleted is null
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        @SuppressWarnings("unchecked")
        List<Group> groupList = queryService.query(hql, params);

        if (groupList == null || groupList.isEmpty()) {
            throw new ServiceException("No user group id info");
        }

        // 2. Extract gid list
        List<String> gidList = groupList.stream()
                .map(Group::getGid)
                .collect(Collectors.toList());

        // 3. Set gidList to requestParam (for remote service call)
        requestParam.setGidList(gidList);

        // 4. Call remote service to get recycle bin short links
        // Convert pageNum to current (if needed)
        Integer current = requestParam.getPageNum() > 0 ? requestParam.getPageNum() : 1;
        Integer size = requestParam.getPageSize() > 0 ? requestParam.getPageSize() : 20;

        Result<PageResponse<ShortLinkPageRespDTO>> result =
                shortLinkActualRemoteService.pageRecycleBinShortLink(gidList, current, size);

        // 5. Handle result
        if (result == null || !result.isSuccess()) {
            log.error("Failed to page query recycle bin short links: {}",
                    result != null ? result.getMessage() : "null result");

            // Return empty page response on error
            PageResponse<ShortLinkPageRespDTO> emptyResponse = new PageResponse<>();
            emptyResponse.setStart((current - 1) * size);
            emptyResponse.setPageSize(size);
            emptyResponse.setTotal(0);
            emptyResponse.setElements(Collections.emptyList());
            return emptyResponse;
        }

        return result.getData();
    }
}
