package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 阅读器账号绑定实体，负责把手机号、邮箱、微信登录标识和联系方式绑定到同一个读者账号。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("reader_account_bind")
public class ReaderAccountBind extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定记录ID。
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 绑定到的读者账号ID。
     */
    private Long accountId;

    /**
     * 绑定类型：PHONE、EMAIL、WECHAT_NO、WECHAT_OPENID、WECHAT_UNIONID。
     */
    private String bindType;

    /**
     * 绑定值。
     */
    private String bindKey;

    /**
     * 绑定来源：LOGIN、BIND、MIGRATE。
     */
    private String bindSource;
}
