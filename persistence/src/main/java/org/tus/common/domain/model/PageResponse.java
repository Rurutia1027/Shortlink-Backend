package org.tus.common.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.tus.common.domain.persistence.SimplePersistedObject;

import java.util.Collection;

/**
 * Wrapper class for returning Persisted Objects in a page friendly format.
 *
 * @param <T> Type of class to be paged
 */
@JsonPropertyOrder(value = {"start", "page_size", "total", "elements"})
public class PageResponse<T extends SimplePersistedObject> implements EntityTag {
    private int start;
    private int pageSize;
    private int total;
    private Collection<T> elements;
    private String entityTag;


    /**
     * @return start of the page
     */
    public int getStart() {
        return start;
    }

    /**
     * Hibernate page inclusive, 0start
     *
     * @param start set the page start
     */
    public void setStart(int start) {
        this.start = start;
    }

    /**
     * (not the number of elements)
     *
     * @return the size of the page
     */
    @JsonProperty("page_size")
    public int getPageSize() {
        return pageSize;
    }

    /**
     * @param pageSize Size of the page
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * Total number of elements, used to see if additional pages should be requested.
     *
     * @return The total number of elements in the request collection
     */
    public int getTotal() {
        return total;
    }

    /**
     * @param total Used by return of hibernate query to tell if more elements are available
     */
    public void setTotal(int total) {
        this.total = total;
    }

    /**
     * Collection of the base elements restricted by the start and pagesize
     *
     * @return collection of base elements
     */
    public Collection<T> getElements() {
        return elements;
    }

    /**
     * @param elements Used to set the elements based on the page information
     */
    public void setElements(Collection<T> elements) {
        this.elements = elements;
    }

    /**
     * return the entity tag of the paged object
     *
     * @return entity tag
     */
    @Override
    public String getEntityTag() {
        return entityTag;
    }

    /**
     * Used to set the entity tag of the paged response,
     * Implementation is dependent on the element type.
     *
     * @param entityTag Entity Tag of the paged Response
     */
    @JsonIgnore
    public void setEntityTag(String entityTag) {
        this.entityTag = entityTag;
    }
}
