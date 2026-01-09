package org.tus.shortlink.svc.service.impl;

import cn.hutool.core.text.StrBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tus.shortlink.base.dto.req.ShortLinkBatchCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkPageReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBatchCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupCountQueryRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.svc.entity.ShortLink;
import org.tus.shortlink.svc.repository.ShortLinkRepository;
import org.tus.shortlink.svc.service.ShortLinkService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    private final String createShortLinkDefaultDomain = "shortlink.tus";
    private final ShortLinkRepository shortLinkRepository;

    @Override
    public void restoreUrl(String shortUri, HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) {
        // TODO
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
    public ShortLinkCreateRespDTO createShortLinkByLock(ShortLinkCreateReqDTO requestParam) {
        // TODO
        return null;
    }

    @Override
    public ShortLinkBatchCreateRespDTO batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        // TODO
        return null;
    }

    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        // TODO
    }

    @Override
    public ShortLinkPageRespDTO pageShortLink(ShortLinkPageReqDTO requestParam) {
        // TODO
        return null;
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

    private String getFavicon(String originUrl) {
        // TODO: fetch favicon URL
        return null;
    }
}
