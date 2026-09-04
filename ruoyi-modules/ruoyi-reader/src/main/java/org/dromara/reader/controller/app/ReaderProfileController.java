package org.dromara.reader.controller.app;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.bo.ReaderProfileUpdateBo;
import org.dromara.reader.domain.vo.app.ReaderProfileVo;
import org.dromara.reader.service.IReaderPortalService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 阅读器应用用户中心控制器，负责个人页相关数据输出。
 */
@RestController
@SaIgnore
@RequiredArgsConstructor
@RequestMapping("/reader/app")
public class ReaderProfileController {

    /**
     * 门户服务入口，负责个人资料、偏好和消息聚合接口。
     */
    private final IReaderPortalService portalService;

    /**
     * 查询当前登录用户资料。
     */
    @GetMapping("/me")
    public R<ReaderProfileVo> me() {
        return R.ok(portalService.getProfile());
    }

    /**
     * 更新当前读者资料。
     */
    @PutMapping("/me")
    public R<Void> updateMe(@RequestBody ReaderProfileUpdateBo bo) {
        portalService.updateProfile(bo);
        return R.ok();
    }

    /**
     * 更新用户偏好设置。
     */
    @PutMapping("/me/preferences")
    public R<Void> preferences(@RequestBody Map<String, Object> preferences) {
        portalService.updatePreferences(preferences);
        return R.ok();
    }

    /**
     * 查询用户消息列表。
     */
    @GetMapping("/messages")
    public R<List<Map<String, Object>>> messages(@RequestParam(required = false) String messageType,
                                                 @RequestParam(required = false) Boolean unreadOnly) {
        return R.ok(portalService.listMessages(messageType, unreadOnly));
    }

    /**
     * 查询未读消息数。
     */
    @GetMapping("/messages/unread-count")
    public R<Long> unreadCount() {
        return R.ok(portalService.countUnreadMessages());
    }

    /**
     * 批量标记消息已读。
     */
    @PutMapping("/messages/read")
    public R<Void> readMessages(@RequestBody Map<String, Object> body) {
        Object ids = body.get("messageIds");
        if (ids instanceof List<?>) {
            List<?> list = (List<?>) ids;
            portalService.markMessagesRead(list.stream().map(item -> Long.valueOf(String.valueOf(item))).toList());
        }
        return R.ok();
    }

    /**
     * 全部消息已读。
     */
    @PutMapping("/messages/read-all")
    public R<Void> readAllMessages() {
        portalService.markAllMessagesRead();
        return R.ok();
    }

    /**
     * 删除消息。
     */
    @DeleteMapping("/messages/{messageId}")
    public R<Void> deleteMessage(@PathVariable Long messageId) {
        portalService.removeMessage(messageId);
        return R.ok();
    }

    /**
     * 批量删除消息。
     */
    @DeleteMapping("/messages")
    public R<Void> deleteMessages(@RequestBody Map<String, Object> body) {
        Object ids = body.get("messageIds");
        if (ids instanceof List<?>) {
            List<?> list = (List<?>) ids;
            portalService.removeMessages(list.stream().map(item -> Long.valueOf(String.valueOf(item))).toList());
        }
        return R.ok();
    }
}
