package org.dromara.reader.domain.vo.app;

import lombok.Data;

import java.util.Map;

/**
 * 阅读器模块代码，承载 ReaderProfileVo 相关业务能力。
 */
@Data
public class ReaderProfileVo {

    /**
     * 用户ID。
     */
    private Long userId;
    /**
     * 昵称。
     */
    private String nickName;
    /**
     * 头像地址。
     */
    private String avatarUrl;

    /**
     * 头像样式标识，先用于演示头像主题与轻量头像方案。
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
     * 手机号。
     */
    private String mobile;

    /**
     * 邮箱。
     */
    private String email;

    /**
     * 微信联系方式展示值。
     */
    private String wechatNo;

    /**
     * 是否处于游客模式。
     */
    private Boolean visitorMode;

    /**
     * 累计积分。
     */
    private Integer pointsTotal;

    /**
     * 今日已得积分。
     */
    private Integer todayPoints;

    /**
     * 连续签到天数。
     */
    private Integer checkinDays;

    /**
     * 今日是否已签到。
     */
    private Boolean claimedToday;

    /**
     * 未读消息数。
     */
    private Long unreadMessageCount;

    /**
     * 偏好设置。
     */
    private Map<String, Object> preferences;
}
