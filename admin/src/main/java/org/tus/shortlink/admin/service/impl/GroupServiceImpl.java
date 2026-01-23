package org.tus.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tus.common.domain.dao.HqlQueryBuilder;
import org.tus.common.domain.persistence.QueryService;
import org.tus.shortlink.admin.entity.Group;
import org.tus.shortlink.admin.entity.GroupUnique;
import org.tus.shortlink.admin.remote.ShortLinkActualRemoteService;
import org.tus.shortlink.admin.service.GroupService;
import org.tus.shortlink.base.common.convention.exception.ClientException;
import org.tus.shortlink.base.common.convention.exception.ServiceException;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.dto.req.ShortLinkGroupSortReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkGroupUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupCountQueryRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupRespDTO;
import org.tus.shortlink.base.tookit.RandomGenerator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Group service implementation using QueryService + HqlQueryBuilder
 * Migrated from MyBatis-based implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {
    private final QueryService queryService;
    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    // TODO: Implement UserContext to get current username
    // For now, we'll need to pass username as parameter or implement UserContext
    // TODO: Implement distributed lock with Redisson
    // private static final String LOCK_GROUP_CREATE_KEY = "lock_group_create_%s"

    @Value("${shortlink.group.max-num:10}")
    private Integer groupMaxNum;

    @Override
    public void saveGroup(String groupName) {
        throw new ServiceException("UserContext not implemented. Please use saveGroup" +
                "(username, groupName");
    }

    @Override
    public void saveGroup(String username, String groupName) {
        // TODO: Implement distributed lock with Redisson
        // RLock lock = redissonClient.getLock(String.format(LOCK_GROUP_CREATE_KEY,
        // username));
        // lock.lock();
        // try {
        //     ...
        // } finally {
        //    lock.unlock()
        // }

        // 1. Check if user has reached max number of groups
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String countHql = builder
                .fromAs(Group.class, "g")
                .selectCount()
                .eq("g.username", username)
                .and()
                .isNull("g.deleted")
                .build();
        Map<String, Object> countParams = builder.getInjectionParameters();
        builder.clear();

        Long count = (Long) queryService.query(countHql, countParams).get(0);
        if (count != null && count >= groupMaxNum) {
            throw new ClientException(String.format("Exceeded maximum group number %d",
                    groupMaxNum));
        }

        // 2. Generate unique gid with retry
        int retryCount = 0;
        int maxRetries = 10;
        String gid = null;

        while (retryCount < maxRetries) {
            gid = saveGroupUniqueReturnedGid();
            if (gid != null && !gid.isEmpty()) {
                // 3. Create Group Entity
                Group group = Group.builder()
                        .gid(gid)
                        .sortOrder(0)
                        .username(username)
                        .build();
                group.setName(groupName); // name is coming from parent class
                // UniqueNamedArtifact
                try {
                    queryService.save(group);
                    // TODO: add to Bloom Filter
                    // gidRegisterCachePenetrationBloomFilter.add(gid)
                } catch (HibernateException e) {
                    // If save fails, retry with new gid
                    log.warn("Failed to save group with gid: {}, retrying ...", gid, e);
                    gid = null;
                    retryCount++;
                }
            } else {
                retryCount++;
            }
        }

        if (gid == null || gid.isEmpty()) {
            throw new ServiceException("Request to generate unique Group ID too frequently!");
        }
    }

    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        // TODO: Get username from UserContext
        // String username = UserContext.getUsername()
        throw new ServiceException("UserContext not implemented. Please implement " +
                "UserContext.getUsername()");
    }

    /**
     * List groups for a specific username
     * This is a helper method that can be called once UserContext is implemented
     */
    public List<ShortLinkGroupRespDTO> listGroup(String username) {
        // 1. Query user's groups
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(Group.class, "g")
                .select("g")
                .isNull("g.deleted")
                .and()
                .eq("g.username", username)
                .orderBy("g.sortOrder", false) // DESC
                .orderBy("g.modifiedDate", false) // DESC (updateTime -> modifiedDate)
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        @SuppressWarnings("unchecked")
        List<Group> groupList = queryService.query(hql, params);

        if (groupList == null || groupList.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Get short link count for each group via remote service
        List<String> gidList = groupList.stream()
                .map(Group::getGid)
                .toList();

        Result<List<ShortLinkGroupCountQueryRespDTO>> listResult =
                shortLinkActualRemoteService.listGroupShortLinkCount(gidList);

        // 3. Map to DTOs and merge short link counts
        List<ShortLinkGroupRespDTO> shortLinkGroupRespDTOList = BeanUtil.copyToList(
                groupList,
                ShortLinkGroupRespDTO.class
        );

        if (listResult != null && listResult.getData() != null) {
            shortLinkGroupRespDTOList.forEach(each -> {
                Optional<ShortLinkGroupCountQueryRespDTO> first = listResult.getData().stream()
                        .filter(item -> Objects.equals(item.getGid(), each.getGid()))
                        .findFirst();
                first.ifPresent(item -> each.setShortLinkCount(first.get().getShortLinkCount()));
            });
        }

        return shortLinkGroupRespDTOList;
    }

    @Override
    @Transactional
    public void updateGroup(ShortLinkGroupUpdateReqDTO requestParam) {
        // TODO: Get username from UserContext
        // String username = UserContext.getUsername()
        throw new ServiceException("UserContext not implemented. Please implement " +
                "UserContext.getUsername()");
    }

    /**
     * Update group for a specific username
     * This is a higher method that can be called once UserContext is implemented
     */
    @Transactional
    public void updateGroup(String username, ShortLinkGroupUpdateReqDTO requestParam) {
        // 1. Query existing group
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(Group.class, "g")
                .select("g")
                .eq("g.username", username)
                .and()
                .eq("g.gid", requestParam.getGid())
                .and()
                .isNull("g.deleted")
                .build();
        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        @SuppressWarnings("unchecked")
        List<Group> results = queryService.query(hql, params);

        if (results == null || results.isEmpty()) {
            throw new ServiceException("Group not found: " + requestParam.getGid());
        }

        if (results.size() > 1) {
            throw new ServiceException("Data inconsistency: duplicate groups for gid: " + requestParam.getGid());
        }

        // 2. Update group name
        Group group = results.get(0);
        group.setName(requestParam.getName());

        // 3. Save (update)
        queryService.save(group, true);
    }

    @Override
    @Transactional
    public void deleteGroup(String gid) {
        // TODO: Get username from UserContext
        // String username = UserContext.getUsername();
        throw new ServiceException("UserContext not implemented. Please implement " +
                "UserContext.getUsername()");
    }

    /**
     * Delete group for a specific username
     * This is a helper method that can be called once UserContext is implemented
     */
    @Transactional
    public void deleteGroup(String username, String gid) {
        // 1. Query existing group
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder
                .fromAs(Group.class, "g")
                .select("g")
                .eq("g.username", username)
                .and()
                .eq("g.gid", gid)
                .and()
                .isNull("g.deleted")
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        @SuppressWarnings("unchecked")
        List<Group> results = queryService.query(hql, params);

        if (results == null || results.isEmpty()) {
            throw new ServiceException("Group not found: " + gid);
        }

        if (results.size() > 1) {
            throw new ServiceException("Data inconsistency: duplicate groups for gid: " + gid);
        }

        // 2. Soft delete (mark as deleted)
        Group group = results.get(0);
        group.markDeleted();

        // 3. Save
        queryService.save(group, true);
    }

    @Override
    @Transactional
    public void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam) {
        // TODO: Get username from UserContext
        // String username = UserContext.getUsername();
        throw new ServiceException("UserContext not implemented. Please implement " +
                "UserContext.getUsername()");
    }

    /**
     * Sort groups for a specific username
     * This is a helper method that can be called once UserContext is implemented
     */
    @Transactional
    public void sortGroup(String username, List<ShortLinkGroupSortReqDTO> requestParam) {
        if (requestParam == null || requestParam.isEmpty()) {
            return;
        }

        for (ShortLinkGroupSortReqDTO each : requestParam) {
            // 1. Query existing group
            HqlQueryBuilder builder = new HqlQueryBuilder();
            String hql = builder
                    .fromAs(Group.class, "g")
                    .select("g")
                    .eq("g.username", username)
                    .and()
                    .eq("g.gid", each.getGid())
                    .and()
                    .isNull("g.deleted")
                    .build();

            Map<String, Object> params = builder.getInjectionParameters();
            builder.clear();

            @SuppressWarnings("unchecked")
            List<Group> results = queryService.query(hql, params);

            if (results == null | results.isEmpty()) {
                log.warn("Group not found for sorting: gid={}, username={}", each.getGid(),
                        username);
                continue;
            }

            if (results.size() > 1) {
                log.warn("Data inconsistency: duplicate groups for gid: {}", each.getGid());
                continue;
            }

            // 2. Update sort order
            Group group = results.get(0);
            group.setSortOrder(each.getSortOrder());

            // 3. save
            queryService.save(group, true);
        }
    }

    /**
     * Save GroupUnique and return gid if successful, null if duplicate
     */
    private String saveGroupUniqueReturnedGid() {
        String gid = RandomGenerator.generateRandom();

        // TODO: Check Bloom Filter first
        // if (gidRegisterCachePenetrationBloomFilter.contains(gid)) {
        //     return null;
        // }
        GroupUnique groupUnique = GroupUnique.builder()
                .gid(gid)
                .build();

        try {
            queryService.save(groupUnique); // Insert only, throws exception if duplicate
            return gid;
        } catch (HibernateException e) {
            // Check if it's a unique constraint violation
            if (e instanceof ConstraintViolationException ||
                    e.getCause() instanceof ConstraintViolationException ||
                    e.getMessage() != null && e.getMessage().contains("unique") ||
                    e.getMessage() != null && e.getMessage().contains("duplicate")) {
                log.debug("Duplicate gid detected: {}", gid);
                return null;
            }
            // Re-throw if it's a different error
            throw e;
        } catch (DataIntegrityViolationException e) {
            // Spring's DataIntegrityViolationException for unique constraint violations
            log.debug("Duplicate gid detected (DataIntegrityViolationException): {}", gid);
            return null;
        }
    }
}
