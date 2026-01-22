package org.tus.shortlink.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tus.common.domain.persistence.PersistedObject;

/**
 * User entity mapped to database via JPA/Hibernate
 * Represents a user in the system
 */
@Entity
@Table(name = "t_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends PersistedObject {

    /**
     * Username
     */
    @Column(nullable = false, length = 256, unique = true)
    private String username;

    /**
     * Password
     */
    @Column(nullable = false, length = 512)
    private String password;

    /**
     * Real name
     */
    @Column(length = 256)
    private String realName;

    /**
     * Phone number
     */
    @Column(length = 128)
    private String phone;

    /**
     * Email address
     */
    @Column(length = 256)
    private String mail;

    /**
     * Deletion timestamp (soft delete)
     */
    @Column(name = "deletion_time")
    private Long deletionTime;
}
