package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器访客账户实体，负责把游客态 visitorId 映射为可持久化的读者主体ID。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_visitor_account")
public class ReaderVisitorAccount extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 访客账户主键ID，同时作为游客态的持久化主体ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 前端生成并长期缓存的访客标识。
     */
    private String visitorId;

    /**
     * 游客昵称，用于“我的”页资料卡与反馈提单回显。
     */
    private String nickName;

    /**
     * 游客头像样式标识，先用于演示头像主题与轻量头像方案。
     */
    private String avatarStyle;

    /**
     * 游客性别。
     */
    private String gender;

    /**
     * 游客手机号展示字段，后续绑定手机号后可回写。
     */
    private String mobile;

    /**
     * 游客微信号展示字段，后续绑定微信后可回写。
     */
    private String wechatNo;

    /**
     * 游客阅读偏好 JSON，保存界面主题、阅读主题、翻页方式等设置。
     */
    private String preferencesJson;

    /**
     * 最近一次活跃时上报的客户端类型。
     */
    private String lastClientType;

    /**
     * 最近一次完成合并的读者账号ID，仅用于留痕。
     */
    private Long linkedUserId;
}
