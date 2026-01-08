package org.tus.shortlink.base.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class ShortLinkBatchCreateReqDTO {

    /**
     * Collection of original URLs
     */
    private List<String> originUrls;

    /**
     * Collection of descriptions
     */
    private List<String> describes;

    /**
     * Group identifier
     */
    private String gid;

    /**
     * Creation type: 0 - API creation, 1 - console creation
     */
    private Integer createdType;

    /**
     * Validity type: 0 - permanent, 1 - custom
     */
    private Integer validDateType;

    /**
     * Validity date
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date validDate;
}
