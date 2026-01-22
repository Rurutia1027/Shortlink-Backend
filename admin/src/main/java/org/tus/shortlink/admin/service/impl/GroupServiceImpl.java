package org.tus.shortlink.admin.service.impl;

import org.springframework.stereotype.Service;
import org.tus.shortlink.admin.service.GroupService;
import org.tus.shortlink.base.dto.req.ShortLinkGroupSortReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkGroupUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupRespDTO;

import java.util.List;

@Service
public class GroupServiceImpl implements GroupService {
    @Override
    public void saveGroup(String groupName) {

    }

    @Override
    public void saveGroup(String username, String groupName) {

    }

    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        return List.of();
    }

    @Override
    public void updateGroup(ShortLinkGroupUpdateReqDTO requestParam) {

    }

    @Override
    public void deleteGroup(String gid) {

    }

    @Override
    public void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam) {

    }
}
