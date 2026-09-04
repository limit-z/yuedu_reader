package org.dromara.reader.service;

import org.dromara.reader.domain.vo.app.AppDiscoverConfigVo;
import org.dromara.reader.domain.vo.app.AppHomeVo;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppTopicDetailVo;
import org.dromara.reader.domain.vo.app.AppWorkCardVo;
import org.dromara.reader.domain.vo.app.ReaderProfileVo;
import org.dromara.reader.domain.bo.ReaderProfileUpdateBo;

import java.util.List;
import java.util.Map;

/**
 * 阅读器模块代码，承载 IReaderPortalService 相关业务能力。
 */
public interface IReaderPortalService {

    /**
     * 获取首页聚合数据。
     */
    AppHomeVo getHome();

    /**
     * 获取发现页配置。
     */
    AppDiscoverConfigVo getDiscoverConfig();

    /**
     * 按条件搜索作品。
     */
    AppPageVo<AppWorkCardVo> searchWorks(String keyword, String workType, Long categoryId, Integer pageNum, Integer pageSize);

    /**
     * 获取专题详情数据。
     */
    AppTopicDetailVo getTopicDetail(String topicKey);

    /**
     * 获取当前用户资料。
     */
    ReaderProfileVo getProfile();

    /**
     * 更新当前用户阅读偏好。
     */
    void updatePreferences(Map<String, Object> preferences);

    /**
     * 更新当前读者资料。
     */
    void updateProfile(ReaderProfileUpdateBo bo);

    /**
     * 查询当前用户消息列表。
     */
    default List<Map<String, Object>> listMessages() {
        return listMessages(null, null);
    }

    /**
     * 查询当前用户消息列表。
     */
    List<Map<String, Object>> listMessages(String messageType, Boolean unreadOnly);

    /**
     * 批量标记消息为已读。
     */
    void markMessagesRead(List<Long> messageIds);

    /**
     * 标记当前用户所有消息为已读。
     */
    void markAllMessagesRead();

    /**
     * 查询当前用户未读消息数。
     */
    Long countUnreadMessages();

    /**
     * 删除单条消息。
     */
    void removeMessage(Long messageId);

    /**
     * 批量删除消息。
     */
    void removeMessages(List<Long> messageIds);
}
