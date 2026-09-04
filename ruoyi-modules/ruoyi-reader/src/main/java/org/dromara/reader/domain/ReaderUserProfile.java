package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器用户主表，负责承载账号状态、登录元数据、基础资料和阅读偏好。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_user_profile")
public class ReaderUserProfile extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 读者账号ID。
     */
    @TableId(value = "account_id")
    private Long accountId;

    /**
     * 账号状态：0正常、1停用。
     */
    private String status;

    /**
     * 最近登录时间。
     */
    private java.time.LocalDateTime lastLoginAt;

    /**
     * 最近登录客户端类型。
     */
    private String lastClientType;

    /**
     * 读者昵称。
     */
    private String nickName;

    /**
     * 读者性别：MALE、FEMALE、UNKNOWN。
     */
    private String gender;

    /**
     * 阅读器端头像样式标识，先用于演示头像主题与轻量头像方案。
     */
    private String avatarStyle;

    /**
     * 读者生日。
     */
    private String birthday;

    /**
     * 所在地区。
     */
    private String region;

    /**
     * 个性签名。
     */
    private String signature;

    /**
     * 阅读偏好 JSON，保存界面主题、阅读主题、字号、翻页方式等客户端设置。
     */
    private String preferencesJson;

}
