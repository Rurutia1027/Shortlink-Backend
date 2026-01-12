package org.tus.shortlink.svc.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tus.common.domain.model.PageResponse;
import org.tus.common.domain.persistence.QueryService;
import org.tus.shortlink.base.dto.req.ShortLinkBatchCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkPageReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBaseInfoRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBatchCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupCountQueryRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.svc.service.ShortLinkService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    @Autowired
    private final QueryService queryService;

    @Override
    public void restoreUrl(String shortUri, HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) {
        // TODO
    }

    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        if (Objects.nonNull(queryService)) {
            return ShortLinkCreateRespDTO.builder()
                    .gid(UUID.randomUUID().toString())
                    .fullShortUrl(UUID.randomUUID().toString())
                    .originUrl(UUID.randomUUID().toString())
                    .build();
        }  else {
            return null;
        }
    }

    @Override
    public ShortLinkCreateRespDTO createShortLinkByLock(ShortLinkCreateReqDTO requestParam) {

        // ---- Basic parameter validation ----
        if (requestParam == null) {
            throw new IllegalArgumentException("requestParam must not be null");
        }
        if (requestParam.getOriginUrl() == null || requestParam.getOriginUrl().isBlank()) {
            throw new IllegalArgumentException("originUrl must not be empty");
        }
        if (requestParam.getDomain() == null || requestParam.getDomain().isBlank()) {
            throw new IllegalArgumentException("domain must not be empty");
        }

        // ---- Simulate lock acquisition (mock) ----
        // TODO: integrate Redis distributed lock later
        // For now we just assume lock is always acquired successfully

        // ---- Generate deterministic short URI ----
        String shortUri = Integer.toHexString(
                Math.abs(requestParam.getOriginUrl().hashCode())
        );

        // Normalize domain
        String domain = requestParam.getDomain();
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }

        String fullShortUrl = domain + "/" + shortUri;

        // ---- Build response ----
        ShortLinkCreateRespDTO resp = new ShortLinkCreateRespDTO();
        resp.setGid(requestParam.getGid() != null ? requestParam.getGid() : "default");
        resp.setOriginUrl(requestParam.getOriginUrl());
        resp.setFullShortUrl(fullShortUrl);

        return resp;
    }

    @Override
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {

        if (requestParam == null || requestParam.getOriginUrls() == null || requestParam.getOriginUrls().isEmpty()) {
            throw new IllegalArgumentException("originUrls must not be empty");
        }

        List<String> originUrls = requestParam.getOriginUrls();
        List<String> describes = requestParam.getDescribes() != null ? requestParam.getDescribes() : Collections.nCopies(originUrls.size(), "");

        if (describes.size() < originUrls.size()) {
            // pad missing descriptions with empty string
            List<String> padded = new ArrayList<>(describes);
            while (padded.size() < originUrls.size()) {
                padded.add("");
            }
            describes = padded;
        }

        List<ShortLinkBaseInfoRespDTO> baseLinkInfos = new ArrayList<>();

        for (int i = 0; i < originUrls.size(); i++) {
            String originUrl = originUrls.get(i);
            String describe = describes.get(i);

            // deterministic short URI based on originUrl hash
            String shortUri = Integer.toHexString(Math.abs(originUrl.hashCode()));
            String domain = "http://short.tus.org"; // mock domain, can be made configurable
            String fullShortUrl = domain + "/" + shortUri;

            ShortLinkBaseInfoRespDTO baseInfo = ShortLinkBaseInfoRespDTO.builder()
                    .originUrl(originUrl)
                    .fullShortUrl(fullShortUrl)
                    .describe(describe)
                    .build();

            baseLinkInfos.add(baseInfo);
        }

        ShortLinkBatchCreateRespDTO resp = new ShortLinkBatchCreateRespDTO();
        resp.setTotal(baseLinkInfos.size());
        resp.setBaseLinkInfos(baseLinkInfos);

        return resp;
    }

    @Override
//    @Transactional(rollbackFor = Exception.class)
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        // TODO
    }

    @Override
    public PageResponse<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        return null;
    }


    /**
     * List the number of active short links per group.
     *
     * @param gidList List of group identifiers
     * @return List of ShortLinkGroupCountQueryRespDTO with gid and active short link count
     */
    @Override
    public List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> gidList) {
        if (gidList == null || gidList.isEmpty()) {
            return Collections.emptyList();
        }

        List<ShortLinkGroupCountQueryRespDTO> result = new ArrayList<>();

        for (String gid : gidList) {
            ShortLinkGroupCountQueryRespDTO dto =
                    ShortLinkGroupCountQueryRespDTO.builder()
                            .gid(gid)
                            .build();
            // mock count, e.g., random between 1 and 100
            dto.setShortLinkCount((int) (Math.random() * 100) + 1);
            result.add(dto);
        }

        return result;
    }
}
