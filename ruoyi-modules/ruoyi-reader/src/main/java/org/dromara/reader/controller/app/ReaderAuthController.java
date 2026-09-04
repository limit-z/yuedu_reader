package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.api.model.EmailLoginBody;
import org.dromara.system.api.model.SmsLoginBody;
import org.dromara.reader.domain.bo.ReaderVisitorMergeBo;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.dromara.reader.service.impl.ReaderAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读器应用认证控制器，当前先承接游客态与正式账号的数据合并能力。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app/auth")
public class ReaderAuthController {

    /**
     * 读者认证服务入口，负责手机号、邮箱登录以及退出登录。
     */
    private final ReaderAuthService authService;

    /**
     * 访客账户服务入口，负责处理游客态数据迁移。
     */
    private final ReaderVisitorAccountService visitorAccountService;

    /**
     * 手机号验证码登录。
     */
    @PostMapping("/mobile-login")
    public R<?> mobileLogin(@RequestBody SmsLoginBody body) {
        return R.ok(authService.mobileLogin(body));
    }

    /**
     * 邮箱验证码登录。
     */
    @PostMapping("/email-login")
    public R<?> emailLogin(@RequestBody EmailLoginBody body) {
        return R.ok(authService.emailLogin(body));
    }

    /**
     * 退出登录。
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    /**
     * 将指定 visitorId 下的游客态数据合并到当前登录账号。
     */
    @PostMapping("/visitor-merge")
    public R<Void> mergeVisitor(@RequestBody ReaderVisitorMergeBo bo) {
        authService.mergeVisitor(bo.getVisitorId());
        return R.ok();
    }
}
