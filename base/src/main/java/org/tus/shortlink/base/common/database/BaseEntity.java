//package org.tus.shortlink.base.common.database;
//
//import jakarta.persistence.Column;
//import jakarta.persistence.GeneratedValue;
//import jakarta.persistence.GenerationType;
//import jakarta.persistence.Id;
//import jakarta.persistence.MappedSuperclass;
//import lombok.Getter;
//import lombok.Setter;
//import org.hibernate.annotations.CreationTimestamp;
//import org.hibernate.annotations.UpdateTimestamp;
//
//import java.time.Instant;
//
///**
// * Base persistent object for all JPA entities.
// * <p>
// * Provides common audit fields and logical delete support.
// */
//// Set deprecated, gonna delete
//@Deprecated
//@Getter
//@Setter
//@MappedSuperclass
//public abstract class BaseEntity {
//
//    /**
//     * Primary key for all entities.
//     */
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    /**
//     * Record creation timestamp.
//     */
//    @CreationTimestamp
//    @Column(name = "create_time", nullable = false, updatable = false)
//    private Instant createTime;
//
//    /**
//     * Record last update timestamp.
//     */
//    @UpdateTimestamp
//    @Column(name = "update_time", nullable = false)
//    private Instant updateTime;
//
//    /**
//     * Logical delete flag.
//     * 0 = not deleted
//     * 1 = deleted
//     */
//    @Column(name = "del_flag", nullable = false)
//    private Integer delFlag = 0;
//}
