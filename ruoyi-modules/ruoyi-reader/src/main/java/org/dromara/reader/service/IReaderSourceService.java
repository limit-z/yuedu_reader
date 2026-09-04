package org.dromara.reader.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderSourcePolicy;
import org.dromara.reader.domain.ReaderSourceChapterSnapshot;
import org.dromara.reader.domain.ReaderSourceError;
import org.dromara.reader.domain.ReaderSourceRule;
import org.dromara.reader.domain.ReaderSourceSite;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.bo.ReaderSourceComplianceBo;
import org.dromara.reader.domain.bo.ReaderSourcePolicyBo;
import org.dromara.reader.domain.bo.ReaderSourceRuleBo;
import org.dromara.reader.domain.bo.ReaderSourceSiteBo;
import org.dromara.reader.domain.bo.ReaderSourceSiteQueryBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderSourceTaskRunAdminVo;

/** 书源采集中心管理服务。 */
public interface IReaderSourceService {

    PageResult<ReaderSourceSite> querySitePage(ReaderSourceSiteQueryBo bo, PageQuery pageQuery);

    Long saveSite(ReaderSourceSiteBo bo);

    void checkCompliance(Long siteId, ReaderSourceComplianceBo bo);

    void updateSiteStatus(Long siteId, boolean enabled);

    PageResult<ReaderSourcePolicy> queryPolicyPage(String policyName, String status, PageQuery pageQuery);

    Long savePolicy(ReaderSourcePolicyBo bo);

    PageResult<ReaderSourceRule> queryRulePage(Long siteId, String status, PageQuery pageQuery);

    Long saveRule(ReaderSourceRuleBo bo);

    void updateRuleStatus(Long ruleId, boolean enabled);

    PageResult<ReaderSourceTask> queryTaskPage(ReaderSourceTaskQueryBo bo, PageQuery pageQuery);

    ReaderSourceTask getTask(Long taskId);

    Long saveTask(ReaderSourceTaskBo bo);

    Long startTask(Long taskId);

    void pauseTask(Long taskId);

    Long resumeTask(Long taskId);

    void cancelTask(Long taskId);

    PageResult<ReaderSourceTaskRunAdminVo> queryTaskRuns(Long taskId, PageQuery pageQuery);

    PageResult<ReaderSourceChapterSnapshot> querySnapshots(Long taskId, String status, PageQuery pageQuery);

    PageResult<ReaderSourceError> queryErrors(Long taskId, String resolved, PageQuery pageQuery);
}
