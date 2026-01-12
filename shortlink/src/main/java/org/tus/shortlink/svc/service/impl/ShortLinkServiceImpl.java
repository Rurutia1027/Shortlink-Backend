package org.tus.shortlink.svc.service.impl;

import cn.hutool.core.lang.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tus.common.domain.model.PageResponse;
import org.tus.common.domain.persistence.QueryService;
import org.tus.shortlink.base.common.convention.exception.ServiceException;
import org.tus.shortlink.base.dto.req.ShortLinkBatchCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkPageReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBaseInfoRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBatchCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupCountQueryRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.base.tookit.HashUtil;
import org.tus.shortlink.svc.entity.ShortLink;
import org.tus.shortlink.svc.entity.ShortLinkGoto;
import org.tus.shortlink.svc.service.ShortLinkService;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    @Value("${shortlink.domain.default}")
    private String createShortLinkDefaultDomain;

    @Autowired
    private final QueryService queryService;

    @Override
    public void restoreUrl(String shortUri, HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) {
        // TODO
    }

    @SneakyThrows
    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        // --- basic validation ---
        if (requestParam == null || requestParam.getOriginUrl() == null || requestParam.getOriginUrl().isBlank()) {
            throw new IllegalArgumentException("originUrl must not be empty");
        }

        // TODO: whitelist check (pending on risk module)
        // verificationWhitelist(requestParam.getOriginUrl());

        // --- generate short uri ---
        String shortLinkSuffix = generateSuffix(requestParam);
        String domain = createShortLinkDefaultDomain;
        String fullShortUrl = domain + "/" + shortLinkSuffix;

        // --- build ShortLink entity ---
        ShortLink shortLink = ShortLink.builder()
                .domain(domain)
                .originUrl(requestParam.getOriginUrl())
                .createdType(requestParam.getCreatedType())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .description(requestParam.getDescribe())
                .shortUri(shortLinkSuffix)
                .enableStatus(0)
                .delTime(0L)
                .fullShortUrl(fullShortUrl)
                .favicon(getFavicon(requestParam.getOriginUrl()))
                .build();

        // --- build goto entity ---
        ShortLinkGoto linkGoto = ShortLinkGoto.builder()
                .fullShortUrl(fullShortUrl)
                .gid(requestParam.getGid())
                .build();

        try {
            // --- persist via Hibernate / QueryService ---
            queryService.save(shortLink);
            queryService.save(linkGoto);
        } catch (Exception ex) {
            // TODO bloom filter / duplicate short uri handling (pending redis module)
            throw new ServiceException(String.format("Unique Shortlink generate failure=%s",
                    fullShortUrl));
        }

        // TODO cache warm-up (pending redis module)
        // TODO bloom filter add (pending redis module)
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl("http://" + fullShortUrl)
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .build();
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


    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        String shorUri;
        String originUrl = requestParam.getOriginUrl();
        originUrl += UUID.randomUUID().toString();
        shorUri = HashUtil.hashToBase62(originUrl);
        // TODO [Redis / Bloom Filter]:
        // 1. The Bloom Filter is currently used to prevent short-link suffix collisions
        //    and to protect against cache penetration.
        // 2. In a later phase, this Bloom Filter should be migrated to Redis
        //    (e.g., RedisBloom module or a custom bitmap-based implementation).
        // 3. The Bloom Filter key should be isolated by domain or business dimension
        //    to avoid global key pollution.
        // 4. Consider a Bloom Filter warm-up strategy during service startup
        //    (e.g., loading existing short links from the database into Redis).
        // 5. Since Bloom Filter do not support deletion, false positives caused by
        //    link expiration or deletion must be handled via a compensation strategy.
        return shorUri;
    }

    @SneakyThrows
    private String getFavicon(String url) {
        URL targetUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (HttpURLConnection.HTTP_OK == responseCode) {
            Document document = Jsoup.connect(url).get();
            Element faviconLink = document.select("link[rel~=(?i)^(shortcut )?icon]").first();
            if (faviconLink != null) {
                return faviconLink.attr("abs:href");
            }
        }
        return null;
    }
}
