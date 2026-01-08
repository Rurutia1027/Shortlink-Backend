package org.tus.shortlink.svc.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.tus.shortlink.base.dto.req.RecycleBinRecoverReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinRemoveReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinSaveReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkRecycleBinPageReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.svc.service.RecycleBinService;

@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {
    @Override
    public void saveRecycle(RecycleBinSaveReqDTO requestParam) {
        // TODO
    }

    @Override
    public Page<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        return null;
    }


    @Override
    public void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        // TODO
    }

    @Override
    public void removeRecycle(RecycleBinRemoveReqDTO requestParam) {
        // TODO
    }
}
