package org.tus.shortlink.svc.service;

import org.tus.shortlink.base.dto.req.RecycleBinRecoverReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinRemoveReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinSaveReqDTO;

public interface RecycleBinService {
    void saveRecycle(RecycleBinSaveReqDTO requestParam);

//    Page<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam);

    void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam);

    void removeRecycle(RecycleBinRemoveReqDTO requestParam);
}
