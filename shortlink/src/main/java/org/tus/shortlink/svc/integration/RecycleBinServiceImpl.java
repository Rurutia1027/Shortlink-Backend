package org.tus.shortlink.svc.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tus.shortlink.base.dto.req.RecycleBinRecoverReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinRemoveReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinSaveReqDTO;
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

//    @Override
//    public Page<ShortLinkPageRespDTO> pageShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
//        // Build Pageable
//        PageRequest pageable = PageRequest.of(
//                requestParam.getPageNum() - 1,
//                requestParam.getPageSize());
//
//        // Mock total count
//        long total = 42L;
//
//        // Mock page content
//        List<ShortLinkPageRespDTO> records = new ArrayList<>();
//
//        int startIndex = pageable.getPageNumber() * pageable.getPageSize();
//        int endIndex = Math.min(startIndex + pageable.getPageSize(), (int) total);
//
//        for (int i = startIndex; i < endIndex; i++) {
//            ShortLinkPageRespDTO dto = ShortLinkPageRespDTO.builder()
//                    .id((long) i + 1)
//                    .domain("http://short.tus.org")
//                    .shortUri("abc" + i)
//                    .fullShortUrl("http://short.tus.org/abc" + i)
//                    .originUrl("https://www.youtube.com/")
//                    .gid(requestParam.getGidList() != null
//                            && !requestParam.getGidList().isEmpty()
//                            ? requestParam.getGidList().get(0)
//                            : "default")
//                    .validDateType(0)
//                    .enableStatus(1) // disable (recycle bin)
//                    .createTime(new Date())
//                    .describe("Mock recycled short link")
//                    .favicon("https://www.youtube.com/favicon.ico")
//                    .build();
//
//            records.add(dto);
//        }
//
//        return new PageImpl<>(records, pageable, total);
//    }

    @Override
    public void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        // TODO: pending on the persistent module
    }

    @Override
    public void removeRecycle(RecycleBinRemoveReqDTO requestParam) {
        // TODO: pending on the persistent module
    }
}
