package org.tus.shortlink.svc.service.impl;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tus.common.domain.dao.HqlQueryBuilder;
import org.tus.common.domain.model.PageResponse;
import org.tus.common.domain.persistence.QueryService;
import org.tus.shortlink.base.common.convention.exception.ServiceException;
import org.tus.shortlink.base.dto.req.RecycleBinRecoverReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinRemoveReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinSaveReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkRecycleBinPageReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.base.tookit.StringUtils;
import org.tus.shortlink.svc.entity.ShortLink;
import org.tus.shortlink.svc.service.RecycleBinService;

import java.util.List;
import java.util.Map;

// TODO: abstract to Persistence Facade in future
// TODO: manage L2 cache / session clear strategy
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {
    private final QueryService queryService;
    @Override
    public void saveRecycle(RecycleBinSaveReqDTO requestParam) {
        // 1. Fetch the existing short link record using HQL query builder
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder.fromAs(ShortLink.class, "sl")
                .open()
                .eq("sl.fullShortUrl", requestParam.getFullShortUrl())
                .eq("sl.gid", requestParam.getGid())
                .eq("sl.enableStatus", 0)
                .isNull("sl.deleted")
                .close()
                .select("sl")
                .build();
        Map<String, Object> params = builder.getInjectionParameters();
        List<ShortLink> shortLinks = queryService.query(hql, params);

        if (shortLinks.isEmpty()) {
            // nothing to update
            return;
        }

        // 2. Update the enableStatus to 1 (move to trash)
        ShortLink toUpdate = shortLinks.get(0);
        toUpdate.setEnableStatus(1);

        // Persist the update using saveOrUpdate logic
        queryService.save(toUpdate, true);

        // TODO: 3. Remove from Redis cache
    }

    @Override
    public PageResponse<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        HqlQueryBuilder builder = new HqlQueryBuilder();

        // Build query for trashed short links
        builder.fromAs(ShortLink.class, "sl")
                .select("sl")
                .eq("sl.enableStatus", 1)
                .isNull("sl.deleted")
                .open()
                .in("sl.gid", requestParam.getGidList())
                .close();

        // Pagination parameters
        int offset = (requestParam.getPageNum() - 1) * requestParam.getPageSize();
        int limit = requestParam.getPageSize();

        // Build HQL and params
        String hql = builder.build();
        Map<String, Object> params = builder.getInjectionParameters();

        // Execute paginated query
        List<ShortLink> entityList = queryService.pagedQuery(hql, params, offset, limit);

        // Fetch total count for pagination
        builder.selectCount();  // replace select with count(*)
        String countHql = builder.build();
        Map<String, Object> countParams = builder.getInjectionParameters();
        Long total = (Long) queryService.query(countHql, countParams).get(0);


        // Map entities to DTOs
        List<ShortLinkPageRespDTO> dtoList = entityList.stream()
                .map(sl -> {
                    ShortLinkPageRespDTO dto = BeanUtil.toBean(sl,
                            ShortLinkPageRespDTO.class);
                    dto.setDomain("http://" + dto.getDomain());
                    return dto;
                }).toList();

        // Construct page response
        PageResponse<ShortLinkPageRespDTO> pageResponse = new PageResponse<>();
        pageResponse.setStart(offset);
        pageResponse.setPageSize(limit);
        pageResponse.setTotal(total.intValue());
        pageResponse.setElements(dtoList);
        return pageResponse;
    }


    @Override
    public void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        if (requestParam == null) {
            throw new IllegalArgumentException("requestParam must not be null");
        }
        if (!StringUtils.hasText(requestParam.getFullShortUrl())) {
            throw new IllegalArgumentException("fullShortUrl must not be empty");
        }

        // 1. Query existing short link (deleted / in trash)
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(ShortLink.class, "sl")
                .select("sl")
                .eq("sl.fullShortUrl", requestParam.getFullShortUrl())
                .and()
                .eq("sl.gid", requestParam.getGid())
                .and()
                .eq("sl.enableStatus", 1) // currently in trash
                .and()
                .isNull("sl.deleted")
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        List<ShortLink> results = queryService.query(hql, params);

        if (results.isEmpty()) {
            throw new ServiceException("Short link not found in recycle bin: "
                    + requestParam.getFullShortUrl());
        }
        if (results.size() > 1) {
            throw new ServiceException("Data inconsistency: duplicate short links in recycle bin for "
                    + requestParam.getFullShortUrl());
        }

        ShortLink shortLink = results.get(0);

        // 2. Apply update in-memory
        shortLink.setEnableStatus(0); // recover from recycle bin

        // 3. Persist changes
        queryService.save(shortLink, true);
    }

    @Override
    public void removeRecycle(RecycleBinRemoveReqDTO requestParam) {
        if (requestParam == null) {
            throw new IllegalArgumentException("requestParam must not be null");
        }
        if (!StringUtils.hasText(requestParam.getFullShortUrl())) {
            throw new IllegalArgumentException("fullShortUrl must not be empty");
        }

        // 1. Query existing short link in recycle bin
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(ShortLink.class, "sl")
                .select("sl")
                .eq("sl.fullShortUrl", requestParam.getFullShortUrl())
                .and()
                .eq("sl.gid", requestParam.getGid())
                .and()
                .eq("sl.enableStatus", 1)  // only links in trash
                .and()
                .eq("sl.delTime", 0L)

                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        List<ShortLink> results = queryService.query(hql, params);

        if (results.isEmpty()) {
            throw new ServiceException("Short link not found in recycle bin: "
                    + requestParam.getFullShortUrl());
        }
        if (results.size() > 1) {
            throw new ServiceException("Data inconsistency: duplicate short links in recycle bin for "
                    + requestParam.getFullShortUrl());
        }

        ShortLink shortLink = results.get(0);

        // 2. Apply deletion flags
        shortLink.setDelTime(System.currentTimeMillis());
        shortLink.markDeleted();

        // 3. Persist changes
        queryService.save(shortLink, true);
    }
}
