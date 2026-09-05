package org.dromara.reader.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderSourceDiscoveryBlacklist;
import org.dromara.reader.domain.ReaderSourceDiscoveryCandidate;
import org.dromara.reader.domain.ReaderSourceDiscoveryProvider;
import org.dromara.reader.domain.ReaderSourceDiscoveryRun;
import org.dromara.reader.domain.bo.ReaderSourceDiscoveryBlacklistBo;
import org.dromara.reader.domain.bo.ReaderSourceDiscoveryCandidateQueryBo;
import org.dromara.reader.domain.bo.ReaderSourceDiscoveryProviderBo;

/** 合规的书源发现中心服务。 */
public interface IReaderSourceDiscoveryService {

    PageResult<ReaderSourceDiscoveryProvider> queryProviderPage(String providerName, String status, PageQuery pageQuery);

    Long saveProvider(ReaderSourceDiscoveryProviderBo bo);

    void updateProviderStatus(Long providerId, boolean enabled);

    Long runProvider(Long providerId);

    PageResult<ReaderSourceDiscoveryBlacklist> queryBlacklistPage(String matcherType, String status, PageQuery pageQuery);

    Long saveBlacklist(ReaderSourceDiscoveryBlacklistBo bo);

    void deleteBlacklist(Long blacklistId);

    void updateBlacklistStatus(Long blacklistId, boolean enabled);

    PageResult<ReaderSourceDiscoveryCandidate> queryCandidatePage(ReaderSourceDiscoveryCandidateQueryBo bo, PageQuery pageQuery);

    void checkCandidate(Long candidateId);

    void approveCandidate(Long candidateId);

    void rejectCandidate(Long candidateId, String reason);

    PageResult<ReaderSourceDiscoveryRun> queryRunPage(Long providerId, PageQuery pageQuery);

    /** 由调度器调用，仅运行到期且已启用的发现源。 */
    void runDueProviders();
}
