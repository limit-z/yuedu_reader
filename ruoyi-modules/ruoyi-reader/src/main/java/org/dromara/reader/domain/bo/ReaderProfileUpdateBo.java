package org.dromara.reader.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 阅读器个人资料更新业务对象，负责承接小程序“个人资料编辑”页提交的数据。
 */
@Data
public class ReaderProfileUpdateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 读者昵称。
     */
    private String nickName;

    /**
     * 头像样式标识，当前先用于演示头像主题与轻量头像方案。
     */
    private String avatarStyle;

    /**
     * 性别：MALE男、FEMALE女、UNKNOWN未知。
     */
    private String gender;

    /**
     * 生日。
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
     * 手机号展示值。
     */
    private String mobile;

    /**
     * 邮箱展示值。
     */
    private String email;

    /**
     * 微信联系方式展示值。
     */
    private String wechatNo;

    /**
     * 预留密码字段，当前读者端验证码登录场景暂不使用。
     */
    private String password;

    /**
     * 阅读器相关偏好设置。
     */
    private Map<String, Object> preferences;
}
