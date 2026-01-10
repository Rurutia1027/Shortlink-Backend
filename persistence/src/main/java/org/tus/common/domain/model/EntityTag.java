package org.tus.common.domain.model;

/**
 * Definition of Entity Tag Methods.
 */
public interface EntityTag {
    /**
     * Compute the entity tag for the object.
     * Used to avoid resending an unchanged object.
     *
     * @return The Entity tag for the object, usually a concatenation of relevant fields
     * with md5 hash
     */
    String getEntityTag();
}
