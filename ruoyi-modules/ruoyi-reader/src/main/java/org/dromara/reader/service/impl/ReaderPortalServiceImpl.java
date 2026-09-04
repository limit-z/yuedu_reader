package org.dromara.reader.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.reader.domain.ReaderAccountBind;
import org.dromara.reader.domain.ReaderHomeBanner;
import org.dromara.reader.domain.ReaderHomeNotice;
import org.dromara.reader.domain.ReaderTopic;
import org.dromara.reader.domain.ReaderTopicWork;
import org.dromara.reader.domain.ReaderUserMessage;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.ReaderUserProfile;
import org.dromara.reader.domain.ReaderVisitorAccount;
import org.dromara.reader.domain.bo.ReaderProfileUpdateBo;
import org.dromara.reader.domain.vo.app.AppDiscoverConfigVo;
import org.dromara.reader.domain.vo.app.AppHomeBannerVo;
import org.dromara.reader.domain.vo.app.AppHomeNoticeVo;
import org.dromara.reader.domain.vo.app.AppHomeSectionVo;
import org.dromara.reader.domain.vo.app.AppHomeVo;
import org.dromara.reader.domain.vo.app.AppPointsVo;
import org.dromara.reader.domain.vo.app.AppPageVo;
import org.dromara.reader.domain.vo.app.AppTopicDetailVo;
import org.dromara.reader.domain.vo.app.AppWorkCardVo;
import org.dromara.reader.domain.vo.app.ReaderProfileVo;
import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.enums.WorkType;
import org.dromara.reader.mapper.ReaderAccountBindMapper;
import org.dromara.reader.mapper.ReaderUserProfileMapper;
import org.dromara.reader.mapper.ReaderHomeBannerMapper;
import org.dromara.reader.mapper.ReaderHomeNoticeMapper;
import org.dromara.reader.mapper.ReaderTopicMapper;
import org.dromara.reader.mapper.ReaderTopicWorkMapper;
import org.dromara.reader.mapper.ReaderUserMessageMapper;
import org.dromara.reader.mapper.ReaderVisitorAccountMapper;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.IReaderPortalService;
import org.dromara.reader.service.IReaderPointsService;
import org.dromara.reader.service.ReaderVisitorAccountService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 阅读器门户聚合服务实现，负责首页、发现页与推荐位的数据拼装。
 */
@RequiredArgsConstructor
@Service
public class ReaderPortalServiceImpl implements IReaderPortalService {

    private static final String BIND_PHONE = "PHONE";
    private static final String BIND_EMAIL = "EMAIL";
    private static final String BIND_WECHAT_NO = "WECHAT_NO";

    /**
     * 作品主表访问入口，负责首页、发现页、搜索页的作品数据来源。
     */
    private final ReaderWorkMapper readerWorkMapper;

    /**
     * 首页公告访问入口，负责书城滚动公告配置查询。
     */
    private final ReaderHomeNoticeMapper readerHomeNoticeMapper;

    /**
     * 首页横幅访问入口，负责轮播推荐位配置查询。
     */
    private final ReaderHomeBannerMapper readerHomeBannerMapper;

    /**
     * 专题主表访问入口，负责首页专题集合配置查询。
     */
    private final ReaderTopicMapper readerTopicMapper;

    /**
     * 专题作品访问入口，负责把专题配置映射成作品卡片列表。
     */
    private final ReaderTopicWorkMapper readerTopicWorkMapper;

    /**
     * 用户消息访问入口，负责消息中心真实数据读取与已读回写。
     */
    private final ReaderUserMessageMapper readerUserMessageMapper;

    /**
     * 访客账户服务入口，负责在未登录时输出游客态资料。
     */
    private final ReaderVisitorAccountService visitorAccountService;

    /**
     * 账号绑定访问入口，负责读取手机号、邮箱、微信联系方式等绑定信息。
     */
    private final ReaderAccountBindMapper readerAccountBindMapper;

    /**
     * 阅读积分服务入口，负责补充个人中心的积分摘要。
     */
    private final IReaderPointsService readerPointsService;

    /**
     * 阅读器用户主表访问入口，负责保存账号状态、基础资料和阅读偏好。
     */
    private final ReaderUserProfileMapper readerUserProfileMapper;

    /**
     * 访客账户访问入口，负责游客态资料直接落库。
     */
    private final ReaderVisitorAccountMapper readerVisitorAccountMapper;

    /**
     * 获取首页聚合数据。
     */
    @Override
    public AppHomeVo getHome() {
        // 首页、发现页和搜索都复用同一批已发布作品，保证推荐与搜索口径一致。
        List<ReaderWork> works = loadPublishedWorks();
        AppHomeVo vo = new AppHomeVo();
        vo.setBanners(buildBanners());
        vo.setNotices(buildNotices());
        vo.setSections(buildSections(works));
        return vo;
    }

    /**
     * 获取发现页配置。
     */
    @Override
    public AppDiscoverConfigVo getDiscoverConfig() {
        List<ReaderWork> works = loadPublishedWorks();
        AppDiscoverConfigVo vo = new AppDiscoverConfigVo();
        // P0 先用静态分类和榜单占位，等运营配置表到位后再替换成动态数据。
        vo.setCategories(buildItems(List.of(
            Map.entry(1L, "小说"),
            Map.entry(2L, "漫画")
        )));
        vo.setRankings(buildItems(List.of(
            Map.entry(1L, "最近更新"),
            Map.entry(2L, "最新上架")
        )));
        vo.setHotKeywords(works.stream()
            .map(ReaderWork::getTitle)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .limit(8)
            .toList());
        return vo;
    }

    /**
     * 按条件搜索作品。
     */
    @Override
    public AppPageVo<AppWorkCardVo> searchWorks(String keyword, String workType, Long categoryId, Integer pageNum, Integer pageSize) {
        // 当前最小闭环先走内存过滤，后续再平滑升级到数据库检索或全文搜索。
        List<AppWorkCardVo> matched = loadPublishedWorks().stream()
            .filter(work -> StrUtil.isBlank(keyword)
                || StrUtil.containsIgnoreCase(work.getTitle(), keyword)
                || StrUtil.containsIgnoreCase(work.getIntro(), keyword))
            .filter(work -> StrUtil.isBlank(workType) || Objects.equals(workType, work.getWorkType()))
            .map(this::toWorkCard)
            .toList();
        return toPage(matched, pageNum, pageSize);
    }

    /**
     * 获取专题详情数据。
     */
    @Override
    public AppTopicDetailVo getTopicDetail(String topicKey) {
        ReaderTopic topic = readerTopicMapper.selectOne(Wrappers.<ReaderTopic>lambdaQuery()
            .eq(ReaderTopic::getTopicKey, topicKey)
            .eq(ReaderTopic::getStatus, "1")
            .and(wrapper -> wrapper.isNull(ReaderTopic::getStartTime).or().le(ReaderTopic::getStartTime, LocalDateTime.now()))
            .and(wrapper -> wrapper.isNull(ReaderTopic::getEndTime).or().ge(ReaderTopic::getEndTime, LocalDateTime.now()))
            .last("limit 1"));
        if (topic == null) {
            throw new ServiceException("专题不存在或已下线");
        }

        List<ReaderTopicWork> relations = readerTopicWorkMapper.selectList(Wrappers.<ReaderTopicWork>lambdaQuery()
            .eq(ReaderTopicWork::getTopicId, topic.getId())
            .orderByAsc(ReaderTopicWork::getSortNo)
            .orderByAsc(ReaderTopicWork::getId));
        if (CollUtil.isEmpty(relations)) {
            throw new ServiceException("专题下暂无作品");
        }

        Map<Long, ReaderWork> workMap = loadPublishedWorks().stream()
            .collect(Collectors.toMap(ReaderWork::getId, item -> item, (left, right) -> left, HashMap::new));

        AppTopicDetailVo detail = new AppTopicDetailVo();
        detail.setTopicKey(topic.getTopicKey());
        detail.setTopicName(topic.getTopicName());
        detail.setTopicDesc(topic.getTopicDesc());
        detail.setCoverUrl(topic.getCoverUrl());
        detail.setBadgeText(topic.getBadgeText());
        detail.setWorkType(topic.getWorkType());
        detail.setItems(relations.stream()
            .map(ReaderTopicWork::getWorkId)
            .map(workMap::get)
            .filter(Objects::nonNull)
            .map(this::toWorkCard)
            .toList());
        return detail;
    }

    /**
     * 获取当前用户资料。
     */
    @Override
    public ReaderProfileVo getProfile() {
        Long accountId = visitorAccountService.resolveCurrentReaderId();
        if (visitorAccountService.isVisitorMode() || accountId == null) {
            return buildVisitorProfile();
        }
        ReaderUserProfile readerProfile = getOrCreateReaderProfile(accountId);
        ReaderProfileVo vo = new ReaderProfileVo();
        vo.setUserId(readerProfile.getAccountId());
        vo.setNickName(StrUtil.blankToDefault(readerProfile.getNickName(), "悦读用户"));
        vo.setAvatarUrl(null);
        vo.setAvatarStyle(readerProfile.getAvatarStyle());
        vo.setGender(StrUtil.blankToDefault(readerProfile.getGender(), "UNKNOWN"));
        vo.setBirthday(readerProfile.getBirthday());
        vo.setRegion(readerProfile.getRegion());
        vo.setSignature(readerProfile.getSignature());
        vo.setMobile(resolveBindValue(accountId, BIND_PHONE));
        vo.setEmail(resolveBindValue(accountId, BIND_EMAIL));
        vo.setWechatNo(resolveBindValue(accountId, BIND_WECHAT_NO));
        vo.setVisitorMode(Boolean.FALSE);
        Map<String, Object> preferences = parsePreferences(readerProfile.getPreferencesJson());
        vo.setPreferences(preferences);
        fillPointsSummary(vo);
        vo.setUnreadMessageCount(countUnreadMessages());
        return vo;
    }

    /**
     * 更新当前读者资料。
     */
    @Override
    public void updateProfile(ReaderProfileUpdateBo bo) {
        if (visitorAccountService.isVisitorMode()) {
            updateVisitorProfile(bo);
            return;
        }
        Long accountId = requireLogin();
        updateLoginProfile(accountId, bo);
    }

    /**
     * 更新当前用户阅读偏好。
     */
    @Override
    public void updatePreferences(Map<String, Object> preferences) {
        if (visitorAccountService.isVisitorMode()) {
            ReaderProfileUpdateBo bo = new ReaderProfileUpdateBo();
            bo.setPreferences(preferences);
            updateVisitorProfile(bo);
            return;
        }
        Long accountId = requireLogin();
        ReaderUserProfile profile = getOrCreateReaderProfile(accountId);
        profile.setPreferencesJson(JSONUtil.toJsonStr(mergePreferences(profile.getPreferencesJson(), preferences)));
        saveReaderProfile(profile);
    }

    /**
     * 查询当前用户消息列表。
     */
    @Override
    public List<Map<String, Object>> listMessages(String messageType, Boolean unreadOnly) {
        Long readerId = visitorAccountService.resolveCurrentReaderId();
        if (readerId == null) {
            return List.of();
        }
        String accountType = visitorAccountService.resolveCurrentAccountType();
        return readerUserMessageMapper.selectList(buildMessageQuery(readerId, accountType, messageType, unreadOnly))
            .stream()
            .map(this::toMessageMap)
            .toList();
    }

    /**
     * 批量标记消息为已读。
     */
    @Override
    public void markMessagesRead(List<Long> messageIds) {
        Long readerId = visitorAccountService.resolveCurrentReaderId();
        if (readerId == null || CollUtil.isEmpty(messageIds)) {
            return;
        }
        String accountType = visitorAccountService.resolveCurrentAccountType();
        // 只允许读者更新自己的消息已读状态，避免通过前端参数误改他人消息。
        readerUserMessageMapper.update(null, Wrappers.<ReaderUserMessage>lambdaUpdate()
            .in(ReaderUserMessage::getId, messageIds)
            .eq(ReaderUserMessage::getReaderId, readerId)
            .eq(ReaderUserMessage::getAccountType, accountType)
            .eq(ReaderUserMessage::getReadStatus, "0")
            .set(ReaderUserMessage::getReadStatus, "1")
            .set(ReaderUserMessage::getReadTime, LocalDateTime.now()));
    }

    /**
     * 标记当前用户所有消息为已读。
     */
    @Override
    public void markAllMessagesRead() {
        Long readerId = visitorAccountService.resolveCurrentReaderId();
        if (readerId == null) {
            return;
        }
        String accountType = visitorAccountService.resolveCurrentAccountType();
        readerUserMessageMapper.update(null, Wrappers.<ReaderUserMessage>lambdaUpdate()
            .eq(ReaderUserMessage::getReaderId, readerId)
            .eq(ReaderUserMessage::getAccountType, accountType)
            .eq(ReaderUserMessage::getReadStatus, "0")
            .set(ReaderUserMessage::getReadStatus, "1")
            .set(ReaderUserMessage::getReadTime, LocalDateTime.now()));
    }

    /**
     * 查询当前用户未读消息数。
     */
    @Override
    public Long countUnreadMessages() {
        Long readerId = visitorAccountService.resolveCurrentReaderId();
        if (readerId == null) {
            return 0L;
        }
        String accountType = visitorAccountService.resolveCurrentAccountType();
        return readerUserMessageMapper.selectCount(Wrappers.<ReaderUserMessage>lambdaQuery()
            .eq(ReaderUserMessage::getReaderId, readerId)
            .eq(ReaderUserMessage::getAccountType, accountType)
            .eq(ReaderUserMessage::getReadStatus, "0"));
    }

    /**
     * 删除单条消息。
     */
    @Override
    public void removeMessage(Long messageId) {
        if (messageId == null) {
            return;
        }
        removeMessages(List.of(messageId));
    }

    /**
     * 批量删除消息。
     */
    @Override
    public void removeMessages(List<Long> messageIds) {
        Long readerId = visitorAccountService.resolveCurrentReaderId();
        if (readerId == null || CollUtil.isEmpty(messageIds)) {
            return;
        }
        String accountType = visitorAccountService.resolveCurrentAccountType();
        readerUserMessageMapper.delete(Wrappers.<ReaderUserMessage>lambdaQuery()
            .eq(ReaderUserMessage::getReaderId, readerId)
            .eq(ReaderUserMessage::getAccountType, accountType)
            .in(ReaderUserMessage::getId, messageIds));
    }

    /**
     * 加载允许在 C 端展示的已发布作品。
     */
    private List<ReaderWork> loadPublishedWorks() {
        return readerWorkMapper.selectList(Wrappers.<ReaderWork>lambdaQuery()
                .eq(ReaderWork::getPublishStatus, PublishStatus.PUBLISHED.name())
                .orderByDesc(ReaderWork::getUpdateTime)
                .orderByDesc(ReaderWork::getCreateTime))
            .stream()
            // 被标记为不可搜索的作品不会参与首页、发现页和搜索结果展示。
            .filter(work -> StrUtil.isBlank(work.getAllowSearch()) || "1".equals(work.getAllowSearch()))
            .toList();
    }

    /**
     * 构造首页横幅数据。
     */
    private List<AppHomeBannerVo> buildBanners() {
        List<ReaderHomeBanner> banners = loadActiveBanners();
        if (CollUtil.isEmpty(banners)) {
            AppHomeBannerVo fallback = new AppHomeBannerVo();
            fallback.setBannerId(1L);
            fallback.setTitle("欢迎来到阅读器");
            fallback.setImageUrl("");
            fallback.setTargetType("URL");
            fallback.setTargetValue("/pages/discover/index");
            return List.of(fallback);
        }
        return banners.stream().map(this::toBannerVo).toList();
    }

    /**
     * 构造首页公告数据。
     */
    private List<AppHomeNoticeVo> buildNotices() {
        List<ReaderHomeNotice> notices = loadActiveNotices();
        if (CollUtil.isEmpty(notices)) {
            AppHomeNoticeVo fallback = new AppHomeNoticeVo();
            fallback.setNoticeId(1L);
            fallback.setTitle("P0 阅读链路已就绪");
            fallback.setContent("首页、详情、阅读、书架与进度同步已打通。");
            fallback.setCreatedAt(LocalDateTime.now());
            return List.of(fallback);
        }
        return notices.stream().map(this::toNoticeVo).toList();
    }

    /**
     * 按首页栏目切分作品列表。
     */
    private List<AppHomeSectionVo> buildSections(List<ReaderWork> works) {
        List<AppHomeSectionVo> sections = new ArrayList<>();
        sections.add(buildSection("latest", "最近更新", works.stream().limit(6).toList()));
        sections.add(buildSection("novel", "小说推荐", works.stream().filter(work -> WorkType.NOVEL.name().equals(work.getWorkType())).limit(6).toList()));
        sections.add(buildSection("comic", "漫画推荐", works.stream().filter(work -> WorkType.COMIC.name().equals(work.getWorkType())).limit(6).toList()));
        // 在保留固定栏目兜底的同时，把专题表配置转成首页区块，便于书城直接透出运营位。
        sections.addAll(buildTopicSections(works));
        return sections.stream().filter(section -> CollUtil.isNotEmpty(section.getItems())).toList();
    }

    /**
     * 构造首页专题栏目列表。
     */
    private List<AppHomeSectionVo> buildTopicSections(List<ReaderWork> works) {
        List<ReaderTopic> topics = loadActiveTopics();
        if (CollUtil.isEmpty(topics)) {
            return List.of();
        }
        Map<Long, ReaderWork> workMap = works.stream()
            .collect(Collectors.toMap(ReaderWork::getId, item -> item, (left, right) -> left, HashMap::new));
        List<Long> topicIds = topics.stream().map(ReaderTopic::getId).toList();
        List<ReaderTopicWork> topicWorks = readerTopicWorkMapper.selectList(Wrappers.<ReaderTopicWork>lambdaQuery()
            .in(ReaderTopicWork::getTopicId, topicIds)
            .orderByAsc(ReaderTopicWork::getTopicId)
            .orderByAsc(ReaderTopicWork::getSortNo)
            .orderByAsc(ReaderTopicWork::getId));
        Map<Long, List<ReaderTopicWork>> topicWorkMap = topicWorks.stream()
            .collect(Collectors.groupingBy(ReaderTopicWork::getTopicId, LinkedHashMap::new, Collectors.toList()));
        return topics.stream()
            .map(topic -> buildTopicSection(topic, topicWorkMap.getOrDefault(topic.getId(), List.of()), workMap))
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 把单个专题转换成首页栏目。
     */
    private AppHomeSectionVo buildTopicSection(ReaderTopic topic, List<ReaderTopicWork> relations, Map<Long, ReaderWork> workMap) {
        List<AppWorkCardVo> items = relations.stream()
            .map(ReaderTopicWork::getWorkId)
            .map(workMap::get)
            .filter(Objects::nonNull)
            .map(this::toWorkCard)
            .limit(6)
            .toList();
        if (CollUtil.isEmpty(items)) {
            return null;
        }
        AppHomeSectionVo section = new AppHomeSectionVo();
        section.setCode(topic.getTopicKey());
        section.setTitle(topic.getTopicName());
        section.setMorePath(StrUtil.blankToDefault(topic.getMorePath(), "/pages/discover/index"));
        section.setItems(items);
        return section;
    }

    /**
     * 构造单个首页栏目。
     */
    private AppHomeSectionVo buildSection(String code, String title, List<ReaderWork> works) {
        AppHomeSectionVo section = new AppHomeSectionVo();
        section.setCode(code);
        section.setTitle(title);
        section.setMorePath("/pages/discover/index");
        section.setItems(works.stream().map(this::toWorkCard).toList());
        return section;
    }

    /**
     * 把作品实体转换成卡片视图对象。
     */
    private AppWorkCardVo toWorkCard(ReaderWork work) {
        AppWorkCardVo vo = new AppWorkCardVo();
        vo.setWorkId(work.getId());
        vo.setTitle(work.getTitle());
        vo.setCoverUrl(work.getCoverUrl());
        vo.setIntro(work.getIntro());
        vo.setWorkType(work.getWorkType());
        vo.setCategoryName(work.getCategoryName());
        vo.setPublishStatus(work.getPublishStatus());
        vo.setTotalChapters(work.getTotalChapters());
        vo.setTotalPages(work.getTotalPages());
        return vo;
    }

    /**
     * 把首页横幅实体转换成 App 端视图对象。
     */
    private AppHomeBannerVo toBannerVo(ReaderHomeBanner banner) {
        AppHomeBannerVo vo = new AppHomeBannerVo();
        vo.setBannerId(banner.getId());
        vo.setTitle(banner.getBannerTitle());
        vo.setImageUrl(StrUtil.blankToDefault(banner.getImageUrl(), ""));
        vo.setTargetType(StrUtil.blankToDefault(banner.getTargetType(), "NONE"));
        vo.setTargetValue(banner.getTargetValue());
        return vo;
    }

    /**
     * 把首页公告实体转换成 App 端视图对象。
     */
    private AppHomeNoticeVo toNoticeVo(ReaderHomeNotice notice) {
        AppHomeNoticeVo vo = new AppHomeNoticeVo();
        vo.setNoticeId(notice.getId());
        vo.setTitle(notice.getNoticeTitle());
        vo.setContent(notice.getNoticeContent());
        vo.setCreatedAt(notice.getCreateTime());
        return vo;
    }

    /**
     * 把消息实体转换成消息中心接口可直接消费的结构。
     */
    private Map<String, Object> toMessageMap(ReaderUserMessage message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messageId", message.getId());
        result.put("title", message.getTitle());
        result.put("content", message.getContent());
        result.put("messageType", message.getMessageType());
        result.put("linkType", message.getLinkType());
        result.put("linkValue", message.getLinkValue());
        result.put("readStatus", "1".equals(message.getReadStatus()));
        result.put("createdAt", message.getCreateTime());
        return result;
    }

    /**
     * 构造消息查询条件。
     */
    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReaderUserMessage> buildMessageQuery(Long readerId, String accountType, String messageType, Boolean unreadOnly) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReaderUserMessage> query = Wrappers.<ReaderUserMessage>lambdaQuery()
            .eq(ReaderUserMessage::getReaderId, readerId)
            .eq(ReaderUserMessage::getAccountType, accountType);
        if (StrUtil.isNotBlank(messageType)) {
            query.eq(ReaderUserMessage::getMessageType, messageType);
        }
        if (Boolean.TRUE.equals(unreadOnly)) {
            query.eq(ReaderUserMessage::getReadStatus, "0");
        }
        return query.orderByDesc(ReaderUserMessage::getCreateTime)
            .orderByDesc(ReaderUserMessage::getId);
    }

    /**
     * 把结果列表切成分页结构。
     */
    private AppPageVo<AppWorkCardVo> toPage(List<AppWorkCardVo> list, Integer pageNum, Integer pageSize) {
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        int fromIndex = Math.min((currentPage - 1) * currentSize, list.size());
        int toIndex = Math.min(fromIndex + currentSize, list.size());
        AppPageVo<AppWorkCardVo> vo = new AppPageVo<>();
        vo.setList(list.subList(fromIndex, toIndex));
        vo.setTotal((long) list.size());
        vo.setPageNum(currentPage);
        vo.setPageSize(currentSize);
        vo.setTotalPages((int) Math.ceil((double) list.size() / currentSize));
        return vo;
    }

    /**
     * 构造发现页分类/榜单选项列表。
     */
    private List<AppDiscoverConfigVo.Item> buildItems(List<Map.Entry<Long, String>> entries) {
        return entries.stream().map(entry -> {
            AppDiscoverConfigVo.Item item = new AppDiscoverConfigVo.Item();
            item.setId(entry.getKey());
            item.setName(entry.getValue());
            return item;
        }).toList();
    }

    /**
     * 校验并返回当前登录用户ID。
     */
    private Long requireLogin() {
        Long userId = visitorAccountService.resolveCurrentReaderId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }
        return userId;
    }

    /**
     * 构造游客态个人资料，并把游客侧偏好 JSON 解析为结构化 Map。
     */
    private ReaderProfileVo buildVisitorProfile() {
        ReaderProfileVo profile = visitorAccountService.buildVisitorProfile();
        ReaderVisitorAccount visitorAccount = visitorAccountService.getCurrentVisitorAccount();
        profile.setPreferences(parsePreferences(visitorAccount == null ? null : visitorAccount.getPreferencesJson()));
        fillPointsSummary(profile);
        profile.setUnreadMessageCount(countUnreadMessages());
        return profile;
    }

    /**
     * 更新登录读者资料，个人资料落在资料表，手机号、邮箱和微信联系方式落在绑定表，偏好落在设置表。
     */
    private void updateLoginProfile(Long accountId, ReaderProfileUpdateBo bo) {
        ReaderUserProfile profile = getOrCreateReaderProfile(accountId);
        if (StrUtil.isNotBlank(bo.getNickName())) {
            profile.setNickName(bo.getNickName());
        }
        if (StrUtil.isNotBlank(bo.getGender())) {
            profile.setGender(bo.getGender());
        }
        if (bo.getBirthday() != null) {
            profile.setBirthday(bo.getBirthday());
        }
        if (bo.getRegion() != null) {
            profile.setRegion(bo.getRegion());
        }
        if (bo.getSignature() != null) {
            profile.setSignature(bo.getSignature());
        }
        if (StrUtil.isNotBlank(bo.getAvatarStyle())) {
            profile.setAvatarStyle(bo.getAvatarStyle());
        }
        if (StrUtil.isNotBlank(bo.getWechatNo())) {
            upsertBind(accountId, BIND_WECHAT_NO, bo.getWechatNo());
        }
        if (StrUtil.isNotBlank(bo.getMobile())) {
            upsertBind(accountId, BIND_PHONE, bo.getMobile());
        }
        if (StrUtil.isNotBlank(bo.getEmail())) {
            upsertBind(accountId, BIND_EMAIL, bo.getEmail());
        }
        if (bo.getPreferences() != null) {
            profile.setPreferencesJson(JSONUtil.toJsonStr(mergePreferences(profile.getPreferencesJson(), bo.getPreferences())));
        }
        saveReaderProfile(profile);
    }

    /**
     * 更新游客资料，让未登录读者也能在“我的”页保存昵称、性别、微信号与偏好。
     */
    private void updateVisitorProfile(ReaderProfileUpdateBo bo) {
        ReaderVisitorAccount account = visitorAccountService.getCurrentVisitorAccount();
        if (account == null) {
            throw new ServiceException("游客标识不存在，请重新进入小程序后再试");
        }
        if (StrUtil.isNotBlank(bo.getNickName())) {
            account.setNickName(bo.getNickName());
        }
        if (StrUtil.isNotBlank(bo.getAvatarStyle())) {
            account.setAvatarStyle(bo.getAvatarStyle());
        }
        if (StrUtil.isNotBlank(bo.getGender())) {
            account.setGender(bo.getGender());
        }
        if (StrUtil.isNotBlank(bo.getMobile())) {
            account.setMobile(bo.getMobile());
        }
        if (StrUtil.isNotBlank(bo.getWechatNo())) {
            account.setWechatNo(bo.getWechatNo());
        }
        if (bo.getPreferences() != null) {
            account.setPreferencesJson(JSONUtil.toJsonStr(mergePreferences(account.getPreferencesJson(), bo.getPreferences())));
        }
        // 游客资料更新只落当前访客账户，不影响后续登录后的系统用户主数据。
        readerVisitorAccountMapper.updateById(account);
    }

    /**
     * 获取或创建登录用户对应的阅读器主表记录。
     */
    private ReaderUserProfile getOrCreateReaderProfile(Long accountId) {
        ReaderUserProfile profile = readerUserProfileMapper.selectById(accountId);
        if (profile != null) {
            requireActiveProfile(profile);
            return profile;
        }
        ReaderUserProfile created = new ReaderUserProfile();
        created.setAccountId(accountId);
        created.setStatus(SystemConstants.NORMAL);
        created.setLastClientType("web");
        created.setNickName("悦读用户");
        created.setGender("UNKNOWN");
        created.setPreferencesJson(JSONUtil.toJsonStr(defaultPreferences()));
        readerUserProfileMapper.insert(created);
        return created;
    }

    /**
     * 校验账号是否可用。
     */
    private void requireActiveProfile(ReaderUserProfile profile) {
        if (profile == null) {
            throw new ServiceException("读者账号不存在");
        }
        if (SystemConstants.DISABLE.equals(profile.getStatus())) {
            throw new ServiceException("读者账号已停用");
        }
    }

    /**
     * 读取当前账号在指定绑定类型下的绑定值。
     */
    private String resolveBindValue(Long accountId, String bindType) {
        ReaderAccountBind bind = readerAccountBindMapper.selectOne(Wrappers.<ReaderAccountBind>lambdaQuery()
            .eq(ReaderAccountBind::getAccountId, accountId)
            .eq(ReaderAccountBind::getBindType, bindType)
            .last("limit 1"));
        return bind == null ? null : bind.getBindKey();
    }

    /**
     * 更新或新增绑定值，确保手机号和邮箱只能绑定到一个读者账号。
     */
    private void upsertBind(Long accountId, String bindType, String bindKey) {
        ReaderAccountBind bind = readerAccountBindMapper.selectOne(Wrappers.<ReaderAccountBind>lambdaQuery()
            .eq(ReaderAccountBind::getAccountId, accountId)
            .eq(ReaderAccountBind::getBindType, bindType)
            .last("limit 1"));
        if (bind != null) {
            if (Objects.equals(bind.getBindKey(), bindKey)) {
                return;
            }
            ReaderAccountBind conflict = readerAccountBindMapper.selectOne(Wrappers.<ReaderAccountBind>lambdaQuery()
                .eq(ReaderAccountBind::getBindType, bindType)
                .eq(ReaderAccountBind::getBindKey, bindKey)
                .last("limit 1"));
            if (conflict != null && !Objects.equals(conflict.getAccountId(), accountId)) {
                throw new ServiceException("该联系方式已被绑定");
            }
            bind.setBindKey(bindKey);
            readerAccountBindMapper.updateById(bind);
            return;
        }
        ReaderAccountBind conflict = readerAccountBindMapper.selectOne(Wrappers.<ReaderAccountBind>lambdaQuery()
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
        created.setBindSource("PROFILE");
        readerAccountBindMapper.insert(created);
    }

    /**
     * 保存阅读器基础资料，主键已存在时更新，不存在时插入。
     */
    private void saveReaderProfile(ReaderUserProfile profile) {
        if (readerUserProfileMapper.selectById(profile.getAccountId()) == null) {
            readerUserProfileMapper.insert(profile);
            return;
        }
        readerUserProfileMapper.updateById(profile);
    }

    /**
     * 把偏好 JSON 解析为结构化 Map，空值时回退到默认配置。
     */
    private Map<String, Object> parsePreferences(String preferencesJson) {
        if (StrUtil.isBlank(preferencesJson)) {
            return defaultPreferences();
        }
        try {
            Map<String, Object> parsed = JSONUtil.toBean(preferencesJson, Map.class);
            if (CollectionUtil.isEmpty(parsed)) {
                return defaultPreferences();
            }
            Map<String, Object> merged = new LinkedHashMap<>(defaultPreferences());
            merged.putAll(parsed);
            return merged;
        } catch (Exception ignored) {
            return defaultPreferences();
        }
    }

    /**
     * 把旧偏好和本次更新做浅合并，避免局部保存覆盖掉其它设置项。
     */
    private Map<String, Object> mergePreferences(String rawPreferencesJson, Map<String, Object> delta) {
        Map<String, Object> merged = new LinkedHashMap<>(parsePreferences(rawPreferencesJson));
        if (delta != null) {
            merged.putAll(delta);
        }
        return merged;
    }

    /**
     * 补充积分摘要，保证“我的”页和积分中心展示口径一致。
     */
    private void fillPointsSummary(ReaderProfileVo profile) {
        if (profile == null) {
            return;
        }
        AppPointsVo points = readerPointsService.getDashboard();
        profile.setPointsTotal(points.getTotalPoints());
        profile.setTodayPoints(points.getTodayPoints());
        profile.setCheckinDays(points.getDays());
        profile.setClaimedToday(points.getClaimed());
    }

    /**
     * 构造默认阅读器偏好，保证无论是游客态还是登录态都有稳定初始值。
     */
    private Map<String, Object> defaultPreferences() {
        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("theme", "warm");
        preferences.put("fontSize", "medium");
        preferences.put("pageMode", "scroll");
        preferences.put("readerTheme", "paper");
        return preferences;
    }

    /**
     * 查询当前处于启用且在生效时间窗内的公告列表。
     */
    private List<ReaderHomeNotice> loadActiveNotices() {
        LocalDateTime now = LocalDateTime.now();
        return readerHomeNoticeMapper.selectList(Wrappers.<ReaderHomeNotice>lambdaQuery()
            .eq(ReaderHomeNotice::getStatus, "1")
            .and(wrapper -> wrapper.isNull(ReaderHomeNotice::getStartTime).or().le(ReaderHomeNotice::getStartTime, now))
            .and(wrapper -> wrapper.isNull(ReaderHomeNotice::getEndTime).or().ge(ReaderHomeNotice::getEndTime, now))
            .orderByAsc(ReaderHomeNotice::getSortNo)
            .orderByDesc(ReaderHomeNotice::getCreateTime)
            .last("limit 10"));
    }

    /**
     * 查询当前处于启用且在生效时间窗内的横幅列表。
     */
    private List<ReaderHomeBanner> loadActiveBanners() {
        LocalDateTime now = LocalDateTime.now();
        return readerHomeBannerMapper.selectList(Wrappers.<ReaderHomeBanner>lambdaQuery()
            .eq(ReaderHomeBanner::getStatus, "1")
            .and(wrapper -> wrapper.isNull(ReaderHomeBanner::getStartTime).or().le(ReaderHomeBanner::getStartTime, now))
            .and(wrapper -> wrapper.isNull(ReaderHomeBanner::getEndTime).or().ge(ReaderHomeBanner::getEndTime, now))
            .orderByAsc(ReaderHomeBanner::getSortNo)
            .orderByDesc(ReaderHomeBanner::getCreateTime)
            .last("limit 10"));
    }

    /**
     * 查询当前处于启用且在生效时间窗内、允许首页展示的专题列表。
     */
    private List<ReaderTopic> loadActiveTopics() {
        LocalDateTime now = LocalDateTime.now();
        return readerTopicMapper.selectList(Wrappers.<ReaderTopic>lambdaQuery()
            .eq(ReaderTopic::getStatus, "1")
            .eq(ReaderTopic::getShowHome, "1")
            .and(wrapper -> wrapper.isNull(ReaderTopic::getStartTime).or().le(ReaderTopic::getStartTime, now))
            .and(wrapper -> wrapper.isNull(ReaderTopic::getEndTime).or().ge(ReaderTopic::getEndTime, now))
            .orderByAsc(ReaderTopic::getSortNo)
            .orderByDesc(ReaderTopic::getCreateTime)
            .last("limit 6"));
    }
}
