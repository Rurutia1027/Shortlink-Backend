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
import org.tus.common.domain.redis.BloomFilterService;
import org.tus.common.domain.redis.CacheService;
import org.tus.shortlink.base.common.constant.RedisConstant;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    private static final String UIP_KEY_PREFIX = "shortlink:stats:uip:";
    private static final Duration UIP_TTL = Duration.ofDays(1);

    @Value("${shortlink.domain.default}")
    private String createShortLinkDefaultDomain;

    @Autowired
    private final QueryService queryService;

    private final ShortLinkStatsEventPublisher shortLinkStatsEventPublisher;

    @Autowired(required = false)
    private CacheService cacheService;

    @Autowired(required = false)
    private BloomFilterService bloomFilterService;

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

        String remoteAddr = getRemoteAddr(httpRequest);
        boolean uipFirstFlag = resolveUipFirstFlag(shortLink.getFullShortUrl(), remoteAddr);

        ShortLinkStatsRecordDTO event = buildStatsRecordFromRequest(shortLink, httpRequest, uipFirstFlag);
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

    /**
     * Resolve UIP first-visit flag using Redis (1-day TTL). When Redis is unavailable, returns false.
     */
    private boolean resolveUipFirstFlag(String fullShortUrl, String remoteAddr) {
        if (cacheService == null || !StringUtils.hasText(remoteAddr)) {
            return false;
        }
        String dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = UIP_KEY_PREFIX + fullShortUrl + ":" + dateStr + ":" + remoteAddr;
        try {
            if (!cacheService.exists(key)) {
                cacheService.set(key, "1", UIP_TTL);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("UIP Redis check/set failed: {}", e.getMessage());
            return false;
        }
    }

    private ShortLinkStatsRecordDTO buildStatsRecordFromRequest(ShortLink shortLink, HttpServletRequest request,
                                                                boolean uipFirstFlag) {
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
                .uipFirstFlag(uipFirstFlag)
                .keys(keys)
                .currentDate(new Date())
                .build();
    }

    private String getRemoteAddr(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "";
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

        // Check Bloom Filter to avoid duplicate suffix (with retry mechanism)
        int maxRetries = 10;
        int retryCount = 0;
        while (retryCount < maxRetries) {
            if (!isSuffixExistsInBloomFilter(fullShortUrl)) {
                break; // Suffix not exists, proceed
            }
            // Suffix might exist (false positive or real collision), regenerate
            log.debug("Short link suffix collision detected (or false positive), regenerating: attempt {}/{}",
                    retryCount + 1, maxRetries);
            shortLinkSuffix = generateSuffix(requestParam);
            fullShortUrl = domain + "/" + shortLinkSuffix;
            retryCount++;
        }

        if (retryCount >= maxRetries) {
            throw new ServiceException("Failed to generate unique short link suffix after " + maxRetries + " attempts");
        }

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

            // Add to Bloom Filter after successful persistence
            addSuffixToBloomFilter(fullShortUrl);

            // TODO cache warm-up: add short link to cache for faster redirect lookup
        } catch (DuplicateKeyException |
                 org.hibernate.exception.ConstraintViolationException ex) {
            // Database unique constraint violation - suffix collision detected
            log.warn("Short link suffix collision detected in database: {}", fullShortUrl, ex);
            // Regenerate and retry (up to maxRetries times)
            throw new ServiceException(String.format("Short link suffix collision: %s. Please retry.", fullShortUrl));
        } catch (Exception ex) {
            log.error("Failed to persist short link: {}", fullShortUrl, ex);
            throw new ServiceException(String.format("Failed to create short link: %s", ex.getMessage()));
        }
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

            // Generate unique suffix with Bloom Filter check and retry
            String shortUri;
            String fullShortUrl;
            int maxRetries = 10;
            int retryCount = 0;

            do {
                shortUri = generateSuffix(
                        ShortLinkCreateReqDTO.builder()
                                .originUrl(originUrl)
                                .build()
                );
                fullShortUrl = StrBuilder.create(createShortLinkDefaultDomain)
                        .append("/")
                        .append(shortUri)
                        .toString();

                // Check Bloom Filter to avoid duplicate suffix
                if (!isSuffixExistsInBloomFilter(fullShortUrl)) {
                    break; // Suffix not exists, proceed
                }
                // Suffix might exist (false positive or real collision), regenerate
                retryCount++;
                if (retryCount >= maxRetries) {
                    throw new ServiceException(
                            String.format("Failed to generate unique short link suffix for URL: %s after %d attempts",
                                    originUrl, maxRetries));
                }
            } while (retryCount < maxRetries);

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

            // Add all suffixes to Bloom Filter after successful persistence
            if (bloomFilterService != null) {
                String filterName = getBloomFilterName();
                for (ShortLink shortLink : shortLinks) {
                    try {
                        bloomFilterService.add(filterName, shortLink.getFullShortUrl());
                    } catch (Exception e) {
                        log.warn("Failed to add short link suffix to Bloom Filter during batch create: {}, error: {}",
                                shortLink.getFullShortUrl(), e.getMessage());
                        // Non-critical error, continue with other items
                    }
                }
            }
        } catch (DuplicateKeyException |
                 org.hibernate.exception.ConstraintViolationException ex) {
            // Database unique constraint violation - suffix collision detected
            log.warn("Batch short link creation failed due to duplicate key", ex);
            throw new ServiceException("Batch short link creation failed due to duplicate suffix. Please retry.");
        } catch (Exception ex) {
            log.error("Batch short link creation failed", ex);
            throw new ServiceException("Batch short link creation failed: " + ex.getMessage());
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


    /**
     * Generate short link suffix using hash algorithm.
     *
     * <p>Algorithm: originUrl + UUID -> Base62 hash
     * This ensures uniqueness even for the same originUrl.
     *
     * @param requestParam Short link creation request
     * @return Short link suffix (e.g., "abc123")
     */
    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        String originUrl = requestParam.getOriginUrl();
        // Append UUID to ensure uniqueness even for same originUrl
        originUrl += UUID.randomUUID().toString();
        return HashUtil.hashToBase62(originUrl);
    }

    /**
     * Check if short link suffix exists in Bloom Filter.
     *
     * <p>Bloom Filter is used to prevent duplicate suffix generation and cache penetration.
     * Note: Bloom Filter may return false positives, but never false negatives.
     *
     * @param fullShortUrl Full short URL (domain + suffix)
     * @return true if suffix might exist (false positive possible), false if definitely not exists
     */
    private boolean isSuffixExistsInBloomFilter(String fullShortUrl) {
        if (bloomFilterService == null) {
            // Bloom Filter not available, skip check (fallback to database constraint)
            return false;
        }
        try {
            String filterName = getBloomFilterName();
            return bloomFilterService.contains(filterName, fullShortUrl);
        } catch (Exception e) {
            log.warn("Bloom Filter check failed for suffix: {}, error: {}", fullShortUrl, e.getMessage());
            // On error, assume not exists to allow database constraint to handle it
            return false;
        }
    }

    /**
     * Add short link suffix to Bloom Filter after successful persistence.
     *
     * <p>This ensures future suffix generation can quickly check for duplicates.
     *
     * @param fullShortUrl Full short URL (domain + suffix)
     */
    private void addSuffixToBloomFilter(String fullShortUrl) {
        if (bloomFilterService == null) {
            log.debug("Bloom Filter service not available, skipping add operation");
            return;
        }
        try {
            String filterName = getBloomFilterName();
            boolean added = bloomFilterService.add(filterName, fullShortUrl);
            if (added) {
                log.debug("Added short link suffix to Bloom Filter: {}", fullShortUrl);
            } else {
                // Element might already exist (false positive from previous check)
                log.debug("Short link suffix already in Bloom Filter (or false positive): {}", fullShortUrl);
            }
        } catch (Exception e) {
            log.warn("Failed to add short link suffix to Bloom Filter: {}, error: {}",
                    fullShortUrl, e.getMessage());
            // Non-critical error, don't fail the request
        }
    }

    /**
     * Get Bloom Filter name for short link suffix deduplication.
     *
     * <p>Filter name is isolated by domain to avoid global key pollution.
     * This allows different domains to have independent Bloom Filters.
     *
     * @return Bloom Filter name
     */
    private String getBloomFilterName() {
        // Isolate by domain to avoid global key pollution
        // Format: short-link:bloom:suffix:{domain}
        return RedisConstant.SHORT_LINK_BLOOM_FILTER_SUFFIX + ":" + createShortLinkDefaultDomain;
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
