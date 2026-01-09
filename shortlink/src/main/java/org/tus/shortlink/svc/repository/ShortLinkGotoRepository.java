package org.tus.shortlink.svc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tus.shortlink.svc.entity.ShortLinkGoto;

import java.util.Optional;

@Repository
public interface ShortLinkGotoRepository extends JpaRepository<ShortLinkGoto, Long> {
    Optional<ShortLinkGoto> findByFullShortUrl(String fullShortUrl);

    Optional<ShortLinkGoto> findByFullShortUrlAndGid(String fullShortUrl, String gid);
}
