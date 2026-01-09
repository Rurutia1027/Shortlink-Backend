package org.tus.shortlink.svc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupCountQueryRespDTO;
import org.tus.shortlink.svc.entity.ShortLink;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    /**
     * Find a short link by gid and full short URL
     */
    Optional<ShortLink> findByGidAndFullShortUrl(String gid, String fullShortUrl);

    /**
     * Disable short link (move to recycle bin)
     * enableStatus: 0 -> 1
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(rollbackFor = Exception.class)
    @Query("""
                update ShortLink s
                   set s.enableStatus = 1
                 where s.gid = :gid
                   and s.fullShortUrl = :fullShortUrl
                   and s.enableStatus = 0
                   and s.delFlag = 0
                   and (s.delTime is null or s.delTime = 0)
            """)
    int disableShortLink(
            @Param("gid") String gid,
            @Param("fullShortUrl") String fullShortUrl
    );

    /**
     * Page short links in recycle bin by gid list
     * Recycle bin definition:
     *  - enableStatus = 1
     *  - delFlag = 0
     *  - delTime IS NOT NULL
     */
    @Query("""
                select s
                from ShortLink s
                where s.gid in :gidList
                  and s.enableStatus = :enableStatus
                  and s.delFlag = 0
                  and s.delTime is not null
            """)
    Page<ShortLink> pageRecycleBin(
            @Param("gidList") List<String> gidList,
            @Param("enableStatus") Integer enableStatus,
            Pageable pageable
    );

    /**
     * Recover a short link from recycle bin
     * enableStatus: 1 -> 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(rollbackFor = Exception.class)
    @Query("""
                update ShortLink s
                   set s.enableStatus = 0
                 where s.gid = :gid
                   and s.fullShortUrl = :fullShortUrl
                   and s.enableStatus = 1
                   and s.delFlag = 0
            """)
    int recoverByGidAndFullShortUrl(
            @Param("gid") String gid,
            @Param("fullShortUrl") String fullShortUrl
    );

    /**
     * Soft delete a short link from recycle bin
     * delFlag: 0 -> 1
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(rollbackFor = Exception.class)
    @Query("""
                update ShortLink s
                   set s.delTime = :delTime,
                       s.delFlag = 1
                 where s.gid = :gid
                   and s.fullShortUrl = :fullShortUrl
                   and s.enableStatus = 1
                   and s.delFlag = 0
                   and (s.delTime is null or s.delTime = 0)
            """)
    int softDeleteByGidAndFullShortUrl(
            @Param("gid") String gid,
            @Param("fullShortUrl") String fullShortUrl,
            @Param("delTime") Long delTime
    );

    /**
     * Find a short link with full state constraints
     */
    Optional<ShortLink> findByGidAndFullShortUrlAndEnableStatusAndDelFlag(
            String gid,
            String fullShortUrl,
            Integer enableStatus,
            Integer delFlag
    );

    /**
     * Page short links by gid and status (non-recycle bin use case)
     */
    @Query("""
                select s
                from ShortLink s
                where s.gid = :gid
                  and s.delFlag = :delFlag
                  and s.enableStatus = :enableStatus
            """)
    Page<ShortLink> pageByGidAndStatus(
            @Param("gid") String gid,
            @Param("delFlag") int delFlag,
            @Param("enableStatus") int enableStatus,
            Pageable pageable
    );

    Page<ShortLink> findByGidAndDelFlagAndEnableStatus(String gid, int delFlag,
                                                       int enableStatus, Pageable pageable);


    @Query("""
                SELECT new org.tus.shortlink.base.dto.resp.ShortLinkGroupCountQueryRespDTO(
                    s.gid, CAST(COUNT(s.id) AS integer)
                )
                FROM ShortLink s
                WHERE s.gid IN :gids
                  AND s.enableStatus = 0
                  AND s.delFlag = 0
                  AND (s.delTime IS NULL OR s.delTime = 0)
                GROUP BY s.gid
            """)
    List<ShortLinkGroupCountQueryRespDTO> countActiveShortLinksByGroup(@Param("gids") List<String> gids);

    Page<ShortLink> findByGidInAndEnableStatusAndDelTimeIsNotNull(List<String> gidList,
                                                                  int enableStatus,
                                                                  PageRequest pageable);
}
