package org.tus.shortlink.svc.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tus.common.domain.model.PageResponse;
import org.tus.shortlink.base.dto.req.RecycleBinRecoverReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinRemoveReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinSaveReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkRecycleBinPageReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.svc.service.RecycleBinService;

// TODO: abstract to Persistence Facade in future
// TODO: manage L2 cache / session clear strategy
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {
    @Override

    public void saveRecycle(RecycleBinSaveReqDTO requestParam) {
        // TODO: pending on the persistent module
    }

    @Override
    public PageResponse<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        // TODO: pending on persistent module
        return null;
    }


    @Override
    public void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        // TODO: pending on the persistent module
    }

    @Override
    public void removeRecycle(RecycleBinRemoveReqDTO requestParam) {
        // TODO: pending on the persistent module
    }
}
