package org.tus.shortlink.svc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tus.common.domain.persistence.UniqueNamedArtifact;

/**
 * Entity representing a short link redirection record
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_link_goto")
public class ShortLinkGoto extends UniqueNamedArtifact {
    /**
     * Group identifier
     */
    @Column(name = "gid", nullable = false, length = 64)
    private String gid;

    /**
     * Full short URL
     */
    @Column(name = "full_short_url", nullable = false, length = 512)
    private String fullShortUrl;
}
