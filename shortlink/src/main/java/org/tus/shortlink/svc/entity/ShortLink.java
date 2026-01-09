package org.tus.shortlink.svc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tus.shortlink.base.common.database.BaseEntity;

import java.time.Instant;
import java.util.Date;

/**
 * ShortLink entity mapped to database via JPA/Hibernate
 */

@Entity
@Table(name = "t_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortLink extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String domain;

    @Column(nullable = false, length = 64)
    private String shortUri;

    @Column(nullable = false, length = 256)
    private String fullShortUrl;

    @Column(nullable = false, length = 2048)
    private String originUrl;

    @Column(nullable = false)
    private Integer clickNum = 0;

    /**
     * Sharding key — MUST NOT be null
     */
    @Column(nullable = false, length = 32)
    private String gid;

    @Column(nullable = false)
    private Integer enableStatus = 0;

    @Column(nullable = false)
    private Integer createdType = 0;

    @Column(nullable = false)
    private Integer validDateType = 0;

    private Date validDate;

    @Column(length = 512)
    private String description;

    private String favicon;

    /**
     * Soft delete timestamp
     */
    private Long delTime;
}
