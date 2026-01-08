package org.tus.shortlink.base.biz;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoDTO {
    /**
     * User ID
     */
    @JSONField(name = "id")
    private String userId;

    /**
     * username
     */
    private String username;

    /**
     * User real name
     */
    private String realName;
}
