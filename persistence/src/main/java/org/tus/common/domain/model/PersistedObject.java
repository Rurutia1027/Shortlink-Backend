package org.tus.common.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.apache.commons.codec.digest.DigestUtils;
import org.tus.shortlink.base.tookit.CodecUtils;

import java.util.Date;

/**
 * Base class for Shortlink entities
 */
@MappedSuperclass
public class PersistedObject extends SimplePersistedObject implements EntityTag {
    /**
     * CreatedDate column identifier
     */
    public static final String PERSISTED_OBJECT_CREATED_DATE_ATTRIBUTE = "CREATED_DATE";
    /**
     * Modified Date column identifier
     */
    public static final String PERSISTED_OBJECT_MODIFIED_DATE_ATTRIBUTE = "MODIFIED_DATE";
    /**
     * Deleted column identifier
     */
    public static final String PERSISTED_OBJECT_DELETED_ATTRIBUTE = "DELETED";
    /**
     * Locked column identifier
     */
    public static final String PERSISTED_OBJECT_LOCKED_COLUMN = "LOCKED";
    public static final String PERSISTED_OBJECT_LOCKED_ATTRIBUTE = "locked";
    /**
     * Disabled column identifier
     */
    public static final String PERSISTED_OBJECT_DISABLED_COLUMN = "IS_DISABLED";
    public static final String PERSISTED_OBJECT_DISABLED_ATTRIBUTE = "disabled";
    /**
     * Is Out Of Sync column identifier
     */
    public static final String PERSISTED_OBJECT_IS_OUT_OF_SYNC_COLUMN = "IS_OUT_OF_SYNC";
    public static final String PERSISTED_OBJECT_IS_OUT_OF_SYNC_ATTRIBUTE = "outOfSync";
    /**
     * Version column identifier
     */
    public static final String PERSISTED_OBJECT_VERSION_ATTRIBUTE = "VERSION_NUMBER";
    /**
     * Entity tag column name
     */
    public static final String EntityTagColumn = "entity_tag";

    private Long versionNumber = 1L;
    private boolean locked;
    private String deleted;
    private Date createdDate;
    private Date modifiedDate;
    private boolean disabled;
    private boolean isOutOfSync;

    /**
     * Creates a new instance of PersistedObject
     */
    public PersistedObject() {
        super();
        Date now = new Date();
        this.createdDate = now;
        this.modifiedDate = now;
    }

    /**
     * Creates a new instance of PersistedObject that is a copy of an existing PersistedObject
     *
     * @param rhs the PersistedObject to copy
     */
    public PersistedObject(PersistedObject rhs) {
        super(rhs);
        this.versionNumber = rhs.versionNumber;
        this.locked = rhs.locked;
        this.deleted = rhs.deleted;
        this.createdDate = rhs.createdDate;
        this.modifiedDate = rhs.modifiedDate;
        this.disabled = rhs.disabled;
        this.isOutOfSync = rhs.isOutOfSync;
    }


    /**
     * Get the version number of the object
     *
     * @return {@link java.lang.Long} version number
     */
    @JsonIgnore
    @Column(name = PersistedObject.PERSISTED_OBJECT_VERSION_ATTRIBUTE)
    @Version
    public Long getVersionNumber() {
        return versionNumber;
    }

    /**
     * Set the version number of the object
     *
     * @param versionNumber {@link java.lang.Long} version number
     */
    public void setVersionNumber(Long versionNumber) {
        this.versionNumber = versionNumber;
    }

    /**
     * Get the timestamp for the object's creation
     *
     * @return {@link java.util.Date} object
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = PersistedObject.PERSISTED_OBJECT_CREATED_DATE_ATTRIBUTE)
    @JsonIgnore
    public Date getCreatedDate() {
        return createdDate;
    }

    /**
     * Set the timestamp of the object's creation
     *
     * @param date {@link java.util.Date} object
     */
    public void setCreatedDate(Date date) {
        this.createdDate = date;
    }

    /**
     * Get the timestamp for the object's modification
     *
     * @return The timestamp for the last time this object was modified.
     */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = PersistedObject.PERSISTED_OBJECT_MODIFIED_DATE_ATTRIBUTE)
    @JsonIgnore
    public Date getModifiedDate() {
        return modifiedDate;
    }

    /**
     * Set the timestamp for the object's modification
     *
     * @param date the timestamp for the last time this object was modified.
     */
    public void setModifiedDate(Date date) {
        this.modifiedDate = date;
    }

    /**
     * Returns a value for the deleted status of the object.  If the value is <tt>null</tt> then the
     * object is not deleted, if it is anything else then the object is considered to be deleted.
     * Typically, the object's UUID will be used, or some other {@link java.lang.String}.
     *
     * @return <tt>null</tt> if not deleted, {@link java.lang.String} value otherwise
     */
    @JsonIgnore
    @Column(name = PersistedObject.PERSISTED_OBJECT_DELETED_ATTRIBUTE)
    public String getDeleted() {
        return deleted;
    }

    /**
     * Set the deleted value of the object to some {@link java.lang.String}
     * value (typically the object's UUID).
     *
     * @param deleted {@link java.lang.String} value
     */
    public void setDeleted(String deleted) {
        this.deleted = deleted;
    }

    /**
     * Returns whether the object has been deleted;
     *
     * @return {@code true} if the object has been marked as deleted, {@code false} otherwise
     * <p>
     * Note: this field must not be named 'isDeleted', otherwise JPA gets confused determining the proper getter for
     * retrieving the 'deleted' property of this class.
     */
    @Transient
    @JsonIgnore
    public boolean isMarkedDeleted() {
        return deleted != null;
    }

    /**
     * Marks the objects as deleted with it's Id
     */
    @JsonIgnore
    public void markDeleted() {
        deleted = getId();
    }

    /**
     * Get the locked status of an object.
     * If <tt>true</tt> then the object should not be allowed to be modified.
     *
     * @return <tt>true</tt> if locked, <tt>false</tt> otherwise
     */
    @JsonIgnore
    @Column(name = PersistedObject.PERSISTED_OBJECT_LOCKED_COLUMN)
    public boolean isLocked() {
        return locked;
    }

    /**
     * Set the locked value to either <tt>true</tt> or <tt>false</tt>.
     *
     * @param locked <tt>true</tt> or <tt>false</tt>
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    /**
     * IS_DISABLED column of the object, boolean value which can be used for enable/disable of an
     * object without having to actually delete the object
     *
     * @return <tt>true</tt> if disabled, <tt>false</tt> otherwise
     */
    @JsonIgnore
    @Column(name = PersistedObject.PERSISTED_OBJECT_DISABLED_COLUMN)
    public boolean isDisabled() {
        return disabled;
    }

    /**
     * Set the disabled value
     *
     * @param disabled <tt>true</tt> or <tt>false</tt>
     */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    /**
     * Boolean value which can be used to flag objects that are not in sync with the data store version
     * of the object.
     * <p>
     * Intended use is that this be set to <tt>true</tt> when an object is being modified, and set to
     * <tt>false</tt> during the saving of the object to the data store.
     *
     * @return <tt>true</tt> if out of sync with data store, <tt>false</tt> otherwise
     */
    @JsonIgnore
    @Column(name = PersistedObject.PERSISTED_OBJECT_IS_OUT_OF_SYNC_COLUMN)
    public boolean getIsOutOfSync() {
        return isOutOfSync;
    }

    /**
     * Set the isOutOfSync value
     *
     * @param isOutOfSync <tt>true</tt> or <tt>false</tt>
     */
    public void setIsOutOfSync(boolean isOutOfSync) {
        this.isOutOfSync = isOutOfSync;
    }



    @Override
    @Transient
    @JsonIgnore
    @Column()
    public String getEntityTag() {
        return CodecUtils.encodeBase64URLSafeString(DigestUtils.sha1(getHashableId()));
    }

    /**
     * Required to have the getEntityTag method, should not be used
     * Entity Tags are generated, not set.
     *
     * @param x should not be used.
     */
    public void setEntityTag(String x) {
    }

    /**
     * Performs object-specific actions when saving
     */
    protected void onSave() {
        setModifiedDate(new Date());
    }
}
