package org.tus.shortlink.admin.service;

import org.tus.shortlink.base.dto.req.ShortLinkGroupSortReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkGroupUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupRespDTO;

import java.util.List;

/**
 * Group service interface for managing short link groups
 */
public interface GroupService {

    /**
     * Create a new short link group
     *
     * @param groupName short link group name
     */
    void saveGroup(String groupName);

    /**
     * Create a new short link group
     *
     * @param username  username
     * @param groupName short link group name
     */
    void saveGroup(String username, String groupName);

    /**
     * List user's short link groups
     *
     * @return list of user's short link groups
     */
    List<ShortLinkGroupRespDTO> listGroup();

    /**
     * Update short link group
     *
     * @param requestParam update group request parameters
     */
    void updateGroup(ShortLinkGroupUpdateReqDTO requestParam);

    /**
     * Delete short link group
     *
     * @param gid short link group identifier
     */
    void deleteGroup(String gid);

    /**
     * Sort short link groups
     *
     * @param requestParam list of group sort request parameters
     */
    void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam);
}
