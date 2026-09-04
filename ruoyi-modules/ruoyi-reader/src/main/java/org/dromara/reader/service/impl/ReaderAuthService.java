package org.dromara.reader.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.GlobalConstants;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.enums.LoginType;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.exception.user.CaptchaExpireException;
import org.dromara.common.core.exception.user.UserException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.utils.ValidatorUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.reader.domain.ReaderAccountBind;
import org.dromara.reader.domain.ReaderUserProfile;
import org.dromara.reader.domain.bo.ReaderVisitorMergeBo;
import org.dromara.reader.mapper.ReaderAccountBindMapper;
import org.dromara.reader.mapper.ReaderUserProfileMapper;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.dromara.system.api.model.EmailLoginBody;
import org.dromara.system.api.model.LoginUser;
import org.dromara.system.api.model.SmsLoginBody;
import org.dromara.system.domain.vo.SysClientVo;
import org.dromara.system.service.ISysClientService;
import org.dromara.reader.domain.vo.app.ReaderLoginVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDateTime;

/**
 * 阅读器认证服务，负责读者 H5 的手机号和邮箱登录。
 */
@Service
@RequiredArgsConstructor
public class ReaderAuthService {

    private static final String BIND_PHONE = "PHONE";
    private static final String BIND_EMAIL = "EMAIL";
    private static final String BIND_WECHAT_NO = "WECHAT_NO";
    private static final String BIND_SOURCE_LOGIN = "LOGIN";
    private static final String DEFAULT_DEVICE_TYPE = "web";

    private final ISysClientService clientService;
    private final ReaderUserProfileMapper readerUserProfileMapper;
    private final ReaderAccountBindMapper accountBindMapper;
    private final ReaderVisitorAccountService visitorAccountService;

    /**
     * 手机号验证码登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReaderLoginVo mobileLogin(SmsLoginBody body) {
        ValidatorUtils.validate(body);
        String phoneNumber = StringUtils.trimToEmpty(body.getPhoneNumber());
        validateSmsCode(phoneNumber, body.getSmsCode());
        ReaderUserProfile profile = resolveOrCreateProfile(BIND_PHONE, phoneNumber);
        return login(profile, body.getClientId());
    }

    /**
     * 邮箱验证码登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReaderLoginVo emailLogin(EmailLoginBody body) {
        ValidatorUtils.validate(body);
        String email = StringUtils.trimToEmpty(body.getEmail());
        validateEmailCode(email, body.getEmailCode());
        ReaderUserProfile profile = resolveOrCreateProfile(BIND_EMAIL, email);
        return login(profile, body.getClientId());
    }

    /**
     * 退出登录。
     */
    public void logout() {
        StpUtil.logout();
    }

    /**
     * 合并游客数据到当前登录账号。
     */
    public void mergeVisitor(String visitorId) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }
        visitorAccountService.mergeVisitorData(visitorId, userId);
    }

    private ReaderLoginVo login(ReaderUserProfile profile, String clientId) {
        SysClientVo client = clientService.queryByClientId(clientId);
        if (ObjectUtil.isNull(client) || !SystemConstants.NORMAL.equals(client.getStatus())) {
            throw new ServiceException("客户端不可用");
        }
        profile.setLastLoginAt(LocalDateTime.now());
        profile.setLastClientType(client.getDeviceType());
        saveProfile(profile);
        LoginUser loginUser = buildLoginUser(profile, client);
        SaLoginParameter model = buildLoginParameter(client);
        LoginHelper.login(loginUser, model);

        ReaderLoginVo loginVo = new ReaderLoginVo();
        loginVo.setAccessToken(StpUtil.getTokenValue());
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(client.getClientId());
        return loginVo;
    }

    private LoginUser buildLoginUser(ReaderUserProfile profile, SysClientVo client) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(profile.getAccountId());
        loginUser.setUsername("reader_" + profile.getAccountId());
        loginUser.setNickname(StringUtils.blankToDefault(profile.getNickName(), "悦读用户"));
        loginUser.setUserType(UserType.READER_USER.getUserType());
        loginUser.setClientKey(client.getClientKey());
        loginUser.setDeviceType(client.getDeviceType());
        return loginUser;
    }

    private SaLoginParameter buildLoginParameter(SysClientVo client) {
        SaLoginParameter model = new SaLoginParameter();
        model.setDeviceType(StringUtils.blankToDefault(client.getDeviceType(), DEFAULT_DEVICE_TYPE));
        model.setTimeout(client.getTimeout());
        model.setActiveTimeout(client.getActiveTimeout());
        model.setExtra(LoginHelper.CLIENT_KEY, client.getClientId());
        model.setExtra(LoginHelper.CLIENT_ACCESS_PATH_KEY, client.getAccessPath());
        model.setExtra(LoginHelper.CLIENT_IP_WHITELIST_KEY, client.getIpWhitelist());
        return model;
    }

    private void validateSmsCode(String phoneNumber, String smsCode) {
        String code = RedisUtils.getCacheObject(GlobalConstants.CAPTCHA_CODE_KEY + phoneNumber);
        if (StringUtils.isBlank(code)) {
            throw new CaptchaExpireException();
        }
        if (!Objects.equals(code, smsCode)) {
            throw new UserException(LoginType.SMS.getRetryLimitCount(), 1);
        }
    }

    private void validateEmailCode(String email, String emailCode) {
        String code = RedisUtils.getCacheObject(GlobalConstants.CAPTCHA_CODE_KEY + email);
        if (StringUtils.isBlank(code)) {
            throw new CaptchaExpireException();
        }
        if (!Objects.equals(code, emailCode)) {
            throw new UserException(LoginType.EMAIL.getRetryLimitCount(), 1);
        }
    }

    private ReaderUserProfile resolveOrCreateProfile(String bindType, String bindKey) {
        ReaderAccountBind bind = accountBindMapper.selectOne(Wrappers.<ReaderAccountBind>lambdaQuery()
            .eq(ReaderAccountBind::getBindType, bindType)
            .eq(ReaderAccountBind::getBindKey, bindKey)
            .last("limit 1"));
        if (bind != null) {
            ReaderUserProfile profile = readerUserProfileMapper.selectById(bind.getAccountId());
            if (profile == null) {
                profile = createProfile(bind.getAccountId(), bindType, bindKey);
            }
            if (SystemConstants.DISABLE.equals(profile.getStatus())) {
                throw new ServiceException("读者账号已停用");
            }
            return profile;
        }

        ReaderUserProfile profile = createProfile(null, bindType, bindKey);
        ensureBind(profile.getAccountId(), bindType, bindKey);
        return profile;
    }

    private ReaderUserProfile createProfile(Long accountId, String bindType, String bindKey) {
        ReaderUserProfile profile = new ReaderUserProfile();
        profile.setAccountId(accountId == null ? IdGeneratorUtil.nextLongId() : accountId);
        profile.setStatus(SystemConstants.NORMAL);
        profile.setLastClientType(DEFAULT_DEVICE_TYPE);
        profile.setNickName(buildNickName(bindType, bindKey));
        profile.setGender("UNKNOWN");
        profile.setPreferencesJson(JSONUtil.toJsonStr(defaultPreferences()));
        saveProfile(profile);
        return profile;
    }

    private void ensureBind(Long accountId, String bindType, String bindKey) {
        ReaderAccountBind bind = accountBindMapper.selectOne(Wrappers.<ReaderAccountBind>lambdaQuery()
            .eq(ReaderAccountBind::getAccountId, accountId)
            .eq(ReaderAccountBind::getBindType, bindType)
            .last("limit 1"));
        if (bind != null) {
            if (!Objects.equals(bind.getBindKey(), bindKey)) {
                ReaderAccountBind conflict = accountBindMapper.selectOne(Wrappers.<ReaderAccountBind>lambdaQuery()
                    .eq(ReaderAccountBind::getBindType, bindType)
                    .eq(ReaderAccountBind::getBindKey, bindKey)
                    .last("limit 1"));
                if (conflict != null && !Objects.equals(conflict.getAccountId(), accountId)) {
                    throw new ServiceException("该联系方式已被绑定");
                }
                bind.setBindKey(bindKey);
                accountBindMapper.updateById(bind);
            }
            return;
        }
        ReaderAccountBind conflict = accountBindMapper.selectOne(Wrappers.<ReaderAccountBind>lambdaQuery()
            .eq(ReaderAccountBind::getBindType, bindType)
            .eq(ReaderAccountBind::getBindKey, bindKey)
            .last("limit 1"));
        if (conflict != null && !Objects.equals(conflict.getAccountId(), accountId)) {
            throw new ServiceException("该联系方式已被绑定");
        }
        ReaderAccountBind created = new ReaderAccountBind();
        created.setAccountId(accountId);
        created.setBindType(bindType);
        created.setBindKey(bindKey);
        created.setBindSource(BIND_SOURCE_LOGIN);
        try {
            accountBindMapper.insert(created);
        } catch (Exception ignored) {
            // 并发登录时可能已由另一请求插入，忽略即可。
        }
    }

    private String buildNickName(String bindType, String bindKey) {
        if (StringUtils.isBlank(bindKey)) {
            return "悦读用户";
        }
        if (BIND_PHONE.equals(bindType) && bindKey.length() >= 7) {
            return bindKey.substring(0, 3) + "****" + bindKey.substring(bindKey.length() - 4);
        }
        int at = bindKey.indexOf('@');
        if (BIND_EMAIL.equals(bindType) && at > 1) {
            return bindKey.substring(0, 2) + "***" + bindKey.substring(at);
        }
        return "悦读用户";
    }

    private Map<String, Object> defaultPreferences() {
        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("theme", "warm");
        preferences.put("fontSize", "medium");
        preferences.put("pageMode", "scroll");
        return preferences;
    }

    private void saveProfile(ReaderUserProfile profile) {
        if (readerUserProfileMapper.selectById(profile.getAccountId()) == null) {
            readerUserProfileMapper.insert(profile);
            return;
        }
        readerUserProfileMapper.updateById(profile);
    }
}
