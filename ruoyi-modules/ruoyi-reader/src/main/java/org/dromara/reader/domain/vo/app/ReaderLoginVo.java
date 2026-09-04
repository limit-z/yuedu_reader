package org.dromara.reader.domain.vo.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 读者端登录返回对象。
 */
@Data
public class ReaderLoginVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expire_in")
    private Long expireIn;

    @JsonProperty("client_id")
    private String clientId;
}
