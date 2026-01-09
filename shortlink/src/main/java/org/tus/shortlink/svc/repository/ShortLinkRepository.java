package org.tus.shortlink.svc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.tus.shortlink.svc.entity.ShortLink;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {
    Optional<ShortLink> findByGidAndFullShortUrl(String gid, String fullShortUrl);

    Optional<ShortLink> findByDomainAndShortUri(String domain, String shortUri);

    /**
     * Disable short link (move to recycle bin)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
                update ShortLink sl
                   set sl.enableStatus = 1
                 where sl.fullShortUrl = :fullShortUrl
                   and sl.gid = :gid
                   and sl.enableStatus = 0
                   and sl.delTime is null
            """)
    int disableShortLink(
            @Param("gid") String gid,
            @Param("fullShortUrl") String fullShortUrl
    );

    /**
     * Find short links in trash by gid list
     */
    Page<ShortLink> findByGidInAndEnableStatusAndDelTimeIsNotNull(
            List<String> gidList,
            Integer enableStatus,
            Pageable pageable
    );


    /**
     * Recover a short link from recycle bin (enableStatus = 1 -> 0)
     *
     * @param gid          Group identifier
     * @param fullShortUrl Full short URL
     * @return number of rows updated
     */
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("UPDATE ShortLink s " +
            "SET s.enableStatus = 0 " +
            "WHERE s.gid = :gid " +
            "AND s.fullShortUrl = :fullShortUrl " +
            "AND s.enableStatus = 1 " +
            "AND s.delTime IS NULL")
    int recoverByGidAndFullShortUrl(String gid, String fullShortUrl);

    /**
     * Soft delete a short link in the recycle bin
     *
     * @param gid          Group identifier
     * @param fullShortUrl Full short URL
     * @param delTime      Current timestamp
     * @return number of rows updated
     */
    @Modifying
    @Transactional(rollbackFor = Exception.class)
    @Query("UPDATE ShortLink s " +
            "SET s.delTime = :delTime, s.delFlag = 1 " +
            "WHERE s.gid = :gid " +
            "AND s.fullShortUrl = :fullShortUrl " +
            "AND s.enableStatus = 1 " +
            "AND (s.delTime IS NULL OR s.delTime = 0) " +
            "AND (s.delFlag IS NULL OR s.delFlag = 0)")
    int softDeleteByGidAndFullShortUrl(String gid, String fullShortUrl, Long delTime);
}
