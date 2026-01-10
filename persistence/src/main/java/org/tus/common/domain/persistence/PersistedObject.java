package org.tus.common.domain.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import org.tus.common.domain.model.EntityTag;

/**
 * Base class for Shortlink entities
 */
@MappedSuperclass
public class PersistedObject extends SimplePersistedObject implements EntityTag {

    /**
     * Disabled column identifier
     */
    public static final String PERSISTED_OBJECT_DISABLED_COLUMN = "IS_DISABLED";

    @JsonIgnore
    @Column(name = PersistedObject.PERSISTED_OBJECT_DISABLED_COLUMN)
    public boolean isDisabled() {
        return false;
    }

    @Override
    @Transient
    @JsonIgnore
    @Column()
    public String getEntityTag() {
        return "";
    }
}
