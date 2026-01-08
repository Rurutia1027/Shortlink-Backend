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
    /**
     * Domain of the short link.
     */
    @Column(nullable = false)
    private String domain;

    /**
     * Short URI (unique code for the short link).
     */
    @Column(nullable = false, unique = true)
    private String shortUri;

    /**
     * Full short URL.
     */
    @Column(nullable = false, unique = true)
    private String fullShortUrl;

    /**
     * Original/origin URL.
     */
    @Column(nullable = false, length = 2048)
    private String originUrl;

    /**
     * Click count.
     */
    @Column(nullable = false)
    private Integer clickNum = 0;

    /**
     * Group ID.
     */
    @Column(nullable = true)
    private String gid;

    /**
     * Enable status: 0 = enabled, 1 = disabled
     */
    @Column(nullable = false)
    private Integer enableStatus = 0;

    /**
     * Created type: 0 = API created, 1 = Console created
     */
    @Column(nullable = false)
    private Integer createdType = 0;

    /**
     * Valid date type: 0 = permanent, 1 = custom
     */
    @Column(nullable = false)
    private Integer validDateType = 0;

    /**
     * Expiration date for the short link (if custom).
     */
    private Instant validDate;

    /**
     * Description.
     */
    @Column(name = "`describe`", length = 512)
    private String describe;

    /**
     * Website favicon.
     */
    private String favicon;

    // TODO PV, UV, UIP should not associated to biz layer code
    // TODO accumulate of metrics data gonna imple in other models and expose invoke api
    //  instead

//    /**
//     * Historical PV/UV/UIP
//     */
//    @Column(nullable = false)
//    private Integer totalPv = 0;
//
//    @Column(nullable = false)
//    private Integer totalUv = 0;
//
//    @Column(nullable = false)
//    private Integer totalUip = 0;
//
//    /**
//     * Today's PV/UV/UIP are transient (not persisted)
//     */
//    @Transient
//    private Integer todayPv;
//
//    @Transient
//    private Integer todayUv;
//
//    @Transient
//    private Integer todayUip;

    /**
     * Deleted timestamp (soft delete)
     */
    private Long delTime;
}
