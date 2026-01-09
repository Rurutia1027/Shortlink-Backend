package org.tus.shortlink.svc.service.impl;

import cn.hutool.core.text.StrBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tus.shortlink.base.common.convention.exception.ClientException;
import org.tus.shortlink.base.common.convention.exception.ServiceException;
import org.tus.shortlink.base.common.enums.VailDateTypeEnum;
import org.tus.shortlink.base.dto.req.ShortLinkBatchCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkPageReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBaseInfoRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBatchCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupCountQueryRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.svc.entity.ShortLink;
import org.tus.shortlink.svc.entity.ShortLinkGoto;
import org.tus.shortlink.svc.repository.ShortLinkGotoRepository;
import org.tus.shortlink.svc.repository.ShortLinkRepository;
import org.tus.shortlink.svc.service.ShortLinkService;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    private final String createShortLinkDefaultDomain = "shortlink.tus";
    private final ShortLinkRepository shortLinkRepository;
    private final ShortLinkGotoRepository shortLinkGotoRepository;

    @Override
    public void restoreUrl(String shortUri, HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) throws IOException {
        String serverName = httpRequest.getServerName();
        String serverPort = Optional.of(httpRequest.getServerPort())
                .filter(port -> port != 80)
                .map(String::valueOf)
                .map(p -> ":" + p)
                .orElse("");
        String fullShortUrl = serverName + serverPort + "/" + shortUri;
        // Query ShortLinkGoto
        Optional<ShortLinkGoto> linkGotoOpt =
                shortLinkGotoRepository.findByFullShortUrl(fullShortUrl);
        if (linkGotoOpt.isEmpty()) {
            httpResponse.sendRedirect("/page/notfound");
            return;
        }

        ShortLinkGoto linkGoto = linkGotoOpt.get();

        // query short link
        Optional<ShortLink> shortLinkOpt = shortLinkRepository
                .findByGidAndFullShortUrlAndEnableStatusAndDelFlag(
                        linkGoto.getGid(),
                        fullShortUrl,
                        0, // enableStatus = 0 (enabled)
                        0 // delFlag = 0 (not delete)
                );

        if (shortLinkOpt.isEmpty()) {
            httpResponse.sendRedirect("/page/notfound");
            return;
        }

        ShortLink shortLink = shortLinkOpt.get();

        // check validate date
        if (shortLink.getValidDate() != null && shortLink.getValidDate().before(new Date())) {
            httpResponse.sendRedirect("/page/notfound");
            return;
        }

        // redirect to full url address
        httpResponse.sendRedirect(shortLink.getOriginUrl());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        String shortLinkSuffix = generateSuffix(requestParam);
        String fullShortUrl = StrBuilder.create(createShortLinkDefaultDomain)
                .append("/")
                .append(shortLinkSuffix)
                .toString();

        // check whether generated full short url duplicated in db
        if (shortLinkRepository.findByGidAndFullShortUrl(requestParam.getGid(),
                fullShortUrl).isPresent()) {
            throw new IllegalStateException(String.format("Shortlink: %s already exists!",
                    fullShortUrl));
        }

        ShortLink shortLink = ShortLink.builder()
                .domain(createShortLinkDefaultDomain)
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .createdType(requestParam.getCreatedType() != null ? requestParam.getCreatedType() : 0)
                .validDateType(requestParam.getValidDateType())
                .description(requestParam.getDescribe())
                .shortUri(shortLinkSuffix)
                .enableStatus(0)
                .delTime(0L)
                .fullShortUrl(fullShortUrl)
                .favicon(getFavicon(requestParam.getOriginUrl()))
                .build();

        ShortLink saved = shortLinkRepository.save(shortLink);

        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl("http://" + saved.getFullShortUrl())
                .originUrl(saved.getOriginUrl())
                .gid(saved.getGid())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShortLinkCreateRespDTO createShortLinkByLock(ShortLinkCreateReqDTO requestParam) {
        // white list validation
        String shortLinkSuffix = generateSuffix(requestParam);
        String fullShortUrl = createShortLinkDefaultDomain + "/" + shortLinkSuffix;

        // create short link
        ShortLink shortLink = ShortLink.builder()
                .domain(createShortLinkDefaultDomain)
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
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
        ShortLinkGoto shortLinkGoto = ShortLinkGoto.builder()
                .fullShortUrl(fullShortUrl)
                .gid(requestParam.getGid())
                .build();

        // insert to db
        try {
            shortLinkRepository.save(shortLink);
            shortLinkGotoRepository.save(shortLinkGoto);
        } catch (Exception ex) {
            throw new ServiceException(String.format("Shortlink:：%s duplicated!",
                    fullShortUrl));
        }

        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl("http://" + fullShortUrl)
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        List<String> originUrls = requestParam.getOriginUrls();
        List<String> describes = requestParam.getDescribes();
        List<ShortLinkBaseInfoRespDTO> result = new ArrayList<>();

        for (int i = 0; i < originUrls.size(); i++) {
            String originUrl = originUrls.get(i);
            String describe = describes.get(i);

            try {
                ShortLinkCreateReqDTO singleReq = ShortLinkCreateReqDTO.builder()
                        .originUrl(originUrl)
                        .describe(describe)
                        .gid(requestParam.getGid())
                        .createdType(requestParam.getCreatedType())
                        .validDateType(requestParam.getValidDateType())
                        .validDate(requestParam.getValidDate())
                        .build();

                ShortLinkCreateRespDTO shortLink = createShortLink(singleReq);

                ShortLinkBaseInfoRespDTO baseInfo = ShortLinkBaseInfoRespDTO.builder()
                        .fullShortUrl(shortLink.getFullShortUrl())
                        .originUrl(shortLink.getOriginUrl())
                        .describe(describe)
                        .build();

                result.add(baseInfo);
            } catch (Exception ex) {
                log.error("Batch short link creation failed for URL: {}", originUrl, ex);
            }
        }

        return ShortLinkBatchCreateRespDTO.builder()
                .total(result.size())
                .baseLinkInfos(result)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        ShortLink hasShortLink =
                shortLinkRepository.findByGidAndFullShortUrlAndEnableStatusAndDelFlag(
                        requestParam.getGid(),
                        requestParam.getFullShortUrl(),
                        0, 0
                ).orElseThrow(() -> new ClientException("Shortlink record does not exist!"));
        if (Objects.equals(hasShortLink.getGid(), requestParam.getGid())) {
            // gid not changed
            hasShortLink.setOriginUrl(requestParam.getOriginUrl());
            hasShortLink.setGid(requestParam.getGid());
            hasShortLink.setDescription(requestParam.getDescribe());
            hasShortLink.setValidDateType(requestParam.getValidDateType());
            hasShortLink.setValidDate(requestParam.getValidDate());
            hasShortLink.setFavicon(
                    Objects.equals(requestParam.getOriginUrl(), hasShortLink.getOriginUrl())
                            ? hasShortLink.getFavicon()
                            : getFavicon(requestParam.getOriginUrl())
            );
            if (Objects.equals(requestParam.getValidDateType(), VailDateTypeEnum.PERMANENT.getType())) {
                hasShortLink.setValidDate(null);
            }

            shortLinkRepository.save(hasShortLink);
        } else {
            // gid already changed

            // TODO: add redis lock here, not for now

            // mark original short link deleted and sync delete status to db
            hasShortLink.setDelFlag(1);
            hasShortLink.setDelTime(System.currentTimeMillis());
            shortLinkRepository.save(hasShortLink);

            // insert new short link to db
            ShortLink newShortLink = ShortLink.builder()
                    .domain(hasShortLink.getDomain())
                    .originUrl(requestParam.getOriginUrl())
                    .gid(requestParam.getGid())
                    .createdType(hasShortLink.getCreatedType())
                    .validDateType(requestParam.getValidDateType())
                    .validDate(requestParam.getValidDate())
                    .description(requestParam.getDescribe())
                    .shortUri(hasShortLink.getShortUri())
                    .enableStatus(hasShortLink.getEnableStatus())
                    .delTime(0L)
                    .fullShortUrl(hasShortLink.getFullShortUrl())
                    .favicon(
                            Objects.equals(requestParam.getOriginUrl(), hasShortLink.getOriginUrl())
                                    ? hasShortLink.getFavicon()
                                    : getFavicon(requestParam.getOriginUrl())
                    )
                    .build();
            shortLinkRepository.save(newShortLink);

            // sync to ShortLinkGoto
            ShortLinkGoto linkGoto = shortLinkGotoRepository
                    .findByFullShortUrlAndGid(hasShortLink.getFullShortUrl(),
                            hasShortLink.getGid())
                    .orElseThrow(() -> new ClientException("ShortLinkGoto not found in db " +
                            "table"));
            linkGoto.setGid(requestParam.getGid());
            shortLinkGotoRepository.save(linkGoto);
        }


    }

    @Override
    public ShortLinkPageRespDTO pageShortLink(ShortLinkPageReqDTO requestParam) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createTime");

        if ("createTime_asc".equalsIgnoreCase(requestParam.getOrderTag())) {
            sort = Sort.by(Sort.Direction.ASC, "createTime");
        } else if ("createTime_desc".equalsIgnoreCase(requestParam.getOrderTag())) {
            sort = Sort.by(Sort.Direction.DESC, "createTime");
        }
        Pageable pageable = PageRequest.of(
                requestParam.getPageNo() - 1,
                requestParam.getPageSize(),
                sort
        );

        Page<ShortLink> page = shortLinkRepository
                .findByGidAndDelFlagAndEnableStatus(
                        requestParam.getGid(),
                        0,
                        0,
                        pageable
                );

        return page.map(each -> {
            ShortLinkPageRespDTO dto = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            dto.setDomain("http://" + dto.getDomain());
            return dto;
        });
    }

    @Override
    public List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> groupIds) {
        // TODO
        return List.of();
    }


    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        // TODO: refine this later
        int length = 6;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int idx = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
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
