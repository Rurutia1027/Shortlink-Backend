package org.tus.common.domain.model;

import org.tus.shortlink.base.common.convention.exception.ServiceException;
import org.tus.shortlink.base.tookit.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.tus.shortlink.base.tookit.StringUtils.UTC_DATE_PATTERN;

/**
 * Wrapper class for returning Persisted Objects in a paged friendly format.
 */
public class Page {
    private Integer start;
    private Integer pageSize;
    private String orderBy;
    private String sort;
    private Date begin;
    private Date end;
    public static String SORT_ASC = "ascending";
    public static String SORT_DESC = "descending";
    public static String ORDER_BY_NAME = "name";
    public static String ORDER_BY_DISPALY_NAME = "displayName";

    public Page() {
        this.start = 0;
        this.pageSize = 20;
        this.orderBy = ORDER_BY_NAME;
        this.sort = SORT_ASC;
    }

    public Page(Integer start, Integer pageSize, String orderBy, String sort) {
        super();
        this.start = (start == null || start < 0) ? 0 : start;
        this.pageSize = (pageSize == null || pageSize < 0) ? 0 : pageSize;
        this.orderBy = orderBy;
        this.sort = sort;
    }

    /**
     * @return start of the page
     */
    public int getStart() {
        return (start == null ? 0 : start);
    }

    /**
     * Hibernate page inclusive, 0start
     *
     * @param start set the page start
     */
    public void setStart(Integer start) {
        this.start = (start == null || start < 0) ? 0 : start;
    }

    /**
     * (not the number of elements)
     *
     * @return the size of the page
     */
    public int getPageSize() {
        return (pageSize == null ? 0 : pageSize);
    }

    /**
     * @param pageSize Size of the page
     */
    public void setPageSize(Integer pageSize) {
        this.pageSize = (pageSize == null || pageSize < 0) ? 20 : pageSize;
        ;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    @Override
    public String toString() {
        return "PageInfo [start=" + start + ", pageSize=" + pageSize + ", orderBy=" + orderBy + ", sort=" + sort
                + ", begin=" + begin + ", end=" + end + "]";
    }

    public Date getBegin() {
        return begin;
    }

    public void setBegin(String begin) {
        try {
            if (StringUtils.hasText(begin)) {
                SimpleDateFormat dateformat = new SimpleDateFormat(UTC_DATE_PATTERN);
                Date date = dateformat.parse(begin);
                this.begin = date;
            }
        } catch (ParseException e) {
            throw new ServiceException("dateBegin:" + begin + " is not a valid UTC Date format");
        }

    }

    public boolean hasBegin() {
        return (this.begin != null);
    }

    public Date getEnd() {
        return end;
    }

    public void setEnd(String end) {
        try {
            if (StringUtils.hasText(end)) {
                SimpleDateFormat dateformat = new SimpleDateFormat(UTC_DATE_PATTERN);
                Date date = dateformat.parse(end);
                this.end = date;
            }
        } catch (ParseException e) {
            throw new ServiceException("dateEnd:" + end + " is not a valid UTC Date format");
        }
    }

    public boolean hasEnd() {
        return (this.end != null);
    }
}
