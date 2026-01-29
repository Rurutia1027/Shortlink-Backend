package org.tus.shortlink.svc.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.text.StrBuilder;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tus.common.domain.dao.HqlQueryBuilder;
import org.tus.common.domain.model.PageResponse;
import org.tus.common.domain.persistence.QueryService;
import org.tus.shortlink.base.common.convention.exception.ServiceException;
import org.tus.shortlink.base.dto.biz.ShortLinkStatsRecordDTO;
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
import org.tus.shortlink.base.tookit.StringUtils;
import org.tus.shortlink.svc.entity.ShortLink;
import org.tus.shortlink.svc.entity.ShortLinkGoto;
import org.tus.shortlink.svc.service.ShortLinkService;
import org.tus.shortlink.svc.service.ShortLinkStatsEventPublisher;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.tus.shortlink.base.tookit.StringUtils.getRemoteAddr;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    @Value("${shortlink.domain.default}")
    private String createShortLinkDefaultDomain;

    @Autowired
    private final QueryService queryService;

    private final ShortLinkStatsEventPublisher shortLinkStatsEventPublisher;

    @Override
    @SneakyThrows
    public void restoreUrl(String shortUri, HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) {
        String fullShortUrl = createShortLinkDefaultDomain + "/" + shortUri;
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(ShortLink.class, "sl")
                .select("sl")
                .eq("sl.fullShortUrl", fullShortUrl)
                .and()
                .eq("sl.delTime", 0L)
                .build();
        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();
        List<ShortLink> results = queryService.query(hql, params);

        if (results == null || results.isEmpty()) {
            httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "Short link not found");
            return;
        }
        if (results.size() > 1) {
            log.warn("restoreUrl: multiple ShortLink for fullShortUrl={}", fullShortUrl);
        }
        ShortLink shortLink = results.get(0);

        ShortLinkStatsRecordDTO event = buildStatsRecordFromRequest(shortLink, httpRequest);
        shortLinkStatsEventPublisher.publish(event);

        String originUrl = shortLink.getOriginUrl();
        if (originUrl == null || originUrl.isBlank()) {
            httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Original URL missing");
            return;
        }
        final String redirectUrl;
        if (!originUrl.startsWith("http://") && !originUrl.startsWith("https://")) {
            redirectUrl = "https://" + originUrl;
        } else {
            redirectUrl = originUrl;
        }
        httpResponse.sendRedirect(redirectUrl);
    }

    private ShortLinkStatsRecordDTO buildStatsRecordFromRequest(ShortLink shortLink, HttpServletRequest request) {
        String keys = UUID.randomUUID().toString();
        String referrer = request.getHeader("Referer");
        String userAgent = request.getHeader("User-Agent");
        if (referrer == null) referrer = "";
        if (userAgent == null) userAgent = "";
        return ShortLinkStatsRecordDTO.builder()
                .gid(shortLink.getGid())
                .fullShortUrl(shortLink.getFullShortUrl())
                .remoteAddr(getRemoteAddr(request))
                .referrer(referrer)
                .userAgent(userAgent)
                .os("Unknown")
                .browser("Unknown")
                .device("Unknown")
                .network("Unknown")
                .uv(null)
                .uvFirstFlag(Boolean.FALSE)
                .uipFirstFlag(Boolean.FALSE)
                .keys(keys)
                .currentDate(new Date())
                .build();
    }

    @SneakyThrows
    @Override
    @Transactional
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
                .gid(requestParam.getGid())
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
    @Transactional
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        List<String> originUrls = requestParam.getOriginUrls();
        if (originUrls == null || originUrls.isEmpty()) {
            return ShortLinkBatchCreateRespDTO.builder()
                    .total(0)
                    .baseLinkInfos(Collections.emptyList())
                    .build();
        }
        List<String> describes = requestParam.getDescribes();
        int size = originUrls.size();

        List<ShortLink> shortLinks = new ArrayList<>(size);
        List<ShortLinkGoto> shortLinkGotos = new ArrayList<>(size);
        List<ShortLinkBaseInfoRespDTO> respList = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            String originUrl = originUrls.get(i);
            String describe = (describes != null && describes.size() > 1)
                    ? describes.get(i)
                    : null;

            // TODO: add this latter
            // verificationWhitelist(originUrl);

            String shortUri = generateSuffix(
                    ShortLinkCreateReqDTO.builder()
                            .originUrl(originUrl)
                            .build()
            );

            String fullShortUrl = StrBuilder.create(createShortLinkDefaultDomain)
                    .append("/")
                    .append(shortUri)
                    .toString();

            ShortLink shortLink = ShortLink.builder()
                    .domain(createShortLinkDefaultDomain)
                    .originUrl(originUrl)
                    .gid(requestParam.getGid())
                    .createdType(requestParam.getCreatedType())
                    .validDateType(requestParam.getValidDateType())
                    .validDate(requestParam.getValidDate())
                    .description(describe)
                    .shortUri(shortUri)
                    .enableStatus(0)
                    .delTime(0L)
                    .fullShortUrl(fullShortUrl)
                    .favicon(getFavicon(originUrl))
                    .build();

            ShortLinkGoto shortLinkGoto = ShortLinkGoto.builder()
                    .fullShortUrl(fullShortUrl)
                    .gid(requestParam.getGid())
                    .build();
            shortLinks.add(shortLink);
            shortLinkGotos.add(shortLinkGoto);

            respList.add(
                    ShortLinkBaseInfoRespDTO.builder()
                            .fullShortUrl("http://" + fullShortUrl)
                            .originUrl(originUrl)
                            .originUrl(requestParam.getGid())
                            .build()
            );
        }

        try {
            queryService.saveAll(shortLinks);
            queryService.saveAll(shortLinkGotos);
        } catch (DuplicateKeyException ex) {
            // TODO: review error isolation strategy (partial success vs retry here!)
            throw new ServiceException("Batch short link creation failed due to duplicate " +
                    "key");
        }

        return ShortLinkBatchCreateRespDTO.builder()
                .total(respList.size())
                .baseLinkInfos(respList)
                .build();
    }

    @SneakyThrows
    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        if (requestParam == null) {
            throw new IllegalArgumentException("requestParam must not be null");
        }

        if (!StringUtils.hasText(requestParam.getFullShortUrl())) {
            throw new IllegalArgumentException("fullShortUrl must not be empty");
        }

        // 1. Query existing short link (by biz key)
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(ShortLink.class, "sl")
                .select("sl")
                .eq("sl.fullShortUrl", requestParam.getFullShortUrl())
                .and()
                .isNull("sl.deleted")
                .build();
        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();
        List<ShortLink> results = queryService.query(hql, params);

        if (results.isEmpty()) {
            throw new ServiceException("Short link with full short url "
                    + requestParam.getFullShortUrl() + " cannot be found in db.");
        }

        if (results.size() > 1) {
            throw new ServiceException("Data inconsistency: duplicate short links for "
                    + requestParam.getFullShortUrl());
        }

        ShortLink shortLink = results.get(0);

        // 2. Apply updates (only mutable fields)
        if (StringUtils.hasText(requestParam.getOriginUrl())) {
            shortLink.setOriginUrl(requestParam.getOriginUrl());
        }

        if (StringUtils.hasText(requestParam.getGid())) {
            shortLink.setGid(requestParam.getGid());
        }

        if (requestParam.getValidDate() != null) {
            shortLink.setValidDate(requestParam.getValidDate());
        }

        if (requestParam.getValidDateType() != null) {
            shortLink.setValidDateType(requestParam.getValidDateType());
        }

        if (StringUtils.hasText(requestParam.getDescribe())) {
            shortLink.setDescription(requestParam.getDescribe());
        }

        // 3. Persist queried/refreshed item back  to db (update path)
        //  @param saveOrUpdate if false, inserts one objects, if exists, throws HibernateException.
        //  If true, check if objects exists, if exists, related object will be updated.
        queryService.save(shortLink, true);
    }

    @Override
    public PageResponse<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        if (requestParam == null) {
            return emptyPage(requestParam);
        }

        int pageNo = requestParam.getPageNo() != null ? requestParam.getPageNo() : 1;
        int pageSize = requestParam.getPageSize() != null ? requestParam.getPageSize() : 10;
        int start = (pageNo - 1) * pageSize;

        HqlQueryBuilder builder = new HqlQueryBuilder();

        // --- base query (filters only) ---
        builder.fromAs(ShortLink.class, "sl")
                .open()
                .eq("sl.gid", requestParam.getGid())
                .close()
                .and()
                .isNull("sl.deleted");

        // --- count query ---
        builder.selectCount();
        String countHql = builder.build();
        Map<String, Object> countParams = builder.getInjectionParameters();

        Long total = (Long) queryService.query(countHql, countParams).get(0);

        if (total == 0L) {
            return emptyPage(requestParam);
        }

        // --- data query ---
        builder.select("sl");

        // ordering
        applyOrder(requestParam.getOrderTag(), builder);

        String hql = builder.build();
        Map<String, Object> hqlParams = builder.getInjectionParameters();
        builder.clear();

        List<ShortLink> records = queryService.pagedQuery(
                hql,
                hqlParams,
                start,
                pageSize
        );


        // --- mapping ---
        List<ShortLinkPageRespDTO> elements = records.stream()
                .map(this::toPageResp)
                .toList();

        // --- response ---
        PageResponse<ShortLinkPageRespDTO> response = new PageResponse<>();
        response.setStart(start);
        response.setPageSize(pageSize);
        response.setTotal(total.intValue());
        response.setElements(elements);
        return response;
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

        HqlQueryBuilder hqlQueryBuilder = new HqlQueryBuilder();
        hqlQueryBuilder
                .fromAs(ShortLink.class, "sl")
                .open()
                .in("sl.gid", gidList)
                .close()
                .and()
                .isNull("sl.deleted")
                .select("sl.gid, count(sl)")
                .groupBy("sl.gid");

        String hql = hqlQueryBuilder.build();
        Map<String, Object> params = hqlQueryBuilder.getInjectionParameters();
        List<Object[]> results = queryService.query(hql, params);

        return results.stream()
                .map(row -> ShortLinkGroupCountQueryRespDTO.builder()
                        .gid((String) row[0])
                        .shortLinkCount(((Long) row[1]).intValue()).build())
                .toList();
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


    // === private methods ===
    private PageResponse<ShortLinkPageRespDTO> emptyPage(ShortLinkPageReqDTO req) {
        PageResponse<ShortLinkPageRespDTO> resp = new PageResponse<>();
        resp.setStart(0);
        resp.setPageSize(req != null ? req.getPageSize() : 10);
        resp.setTotal(0);
        resp.setElements(List.of());
        return resp;
    }

    // Entity -> DTO
    private ShortLinkPageRespDTO toPageResp(ShortLink sl) {
        return ShortLinkPageRespDTO.builder()
                .id(sl.getId())
                .domain(sl.getDomain())
                .shortUri(sl.getShortUri())
                .fullShortUrl(sl.getFullShortUrl())
                .originUrl(sl.getOriginUrl())
                .gid(sl.getGid())
                .validDateType(sl.getValidDateType())
                .enableStatus(sl.getEnableStatus())
                .validDate(sl.getValidDate())
                .createTime(sl.getCreatedDate())
                .describe(sl.getDescription())
                .favicon(sl.getFavicon())
                .build();
    }

    private void applyOrder(String orderTag, HqlQueryBuilder builder) {
        if ("created_time_asc".equals(orderTag)) {
            builder.orderBy("sl.createdDate", true);
        } else {
            // default: newest first
            builder.orderBy("sl.createdDate", false);
        }
    }
}
