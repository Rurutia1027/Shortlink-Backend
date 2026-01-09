package org.tus.shortlink.svc.service.impl;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tus.shortlink.base.dto.req.RecycleBinRecoverReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinRemoveReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinSaveReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkRecycleBinPageReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.svc.entity.ShortLink;
import org.tus.shortlink.svc.repository.ShortLinkRepository;
import org.tus.shortlink.svc.service.RecycleBinService;

import java.time.Instant;

// TODO: abstract to Persistence Facade in future
// TODO: manage L2 cache / session clear strategy
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {
    private final ShortLinkRepository shortLinkRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRecycle(RecycleBinSaveReqDTO requestParam) {
        int updated = shortLinkRepository.disableShortLink(
                requestParam.getGid(),
                requestParam.getFullShortUrl()
        );

        if (updated == 0) {
            throw new IllegalStateException(
                    "Short link not found, already disabled, or " +
                            "already deleted");
        }
    }

    @Override
    public Page<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        // build Pageable
        PageRequest pageable = PageRequest.of(requestParam.getPageNum() - 1,
                requestParam.getPageSize());

        // Query Recycled Items
        Page<ShortLink> page = shortLinkRepository.findByGidInAndEnableStatusAndDelTimeIsNotNull(
                requestParam.getGidList(),
                1,
                pageable);

        // Convert entity into DTO
        return page.map(each -> {
                ShortLinkPageRespDTO dto = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
                dto.setDomain("http://" + dto.getDomain());
                return dto;
        });
    }


    @Override
    public void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        int updated = shortLinkRepository.recoverByGidAndFullShortUrl(
                requestParam.getGid(),
                requestParam.getFullShortUrl()
        );

        if (updated == 0) {
            throw new IllegalStateException(
                    "Short link not found, already enabled, or cannot be recovered"
            );
        }
    }

    @Override
    public void removeRecycle(RecycleBinRemoveReqDTO requestParam) {
        Long currentTime = Instant.now().toEpochMilli();

        int updated = shortLinkRepository.softDeleteByGidAndFullShortUrl(
                requestParam.getGid(),
                requestParam.getFullShortUrl(),
                currentTime
        );

        if (updated == 0) {
            throw new IllegalStateException(
                    "Short link not found, already deleted, or not in recycle bin"
            );
        }
    }
}
