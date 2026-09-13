package org.dromara.reader.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.reader.domain.ReaderSourcePolicy;
import org.dromara.reader.domain.ReaderSourceChapterSnapshot;
import org.dromara.reader.domain.ReaderSourceError;
import org.dromara.reader.domain.ReaderSourceRule;
import org.dromara.reader.domain.ReaderSourceSite;
import org.dromara.reader.domain.ReaderSourceTask;
import org.dromara.reader.domain.ReaderSourceTaskBook;
import org.dromara.reader.domain.ReaderSourceTaskFallback;
import org.dromara.reader.domain.ReaderSourceTaskLog;
import org.dromara.reader.domain.bo.ReaderSourceComplianceBo;
import org.dromara.reader.domain.bo.ReaderSourcePolicyBo;
import org.dromara.reader.domain.bo.ReaderSourceRuleBo;
import org.dromara.reader.domain.bo.ReaderSourceSiteBo;
import org.dromara.reader.domain.bo.ReaderSourceSiteQueryBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskQueryBo;
import org.dromara.reader.domain.bo.ReaderSourceTaskBookQueryBo;
import org.dromara.reader.domain.vo.admin.ReaderSourceTaskRunAdminVo;
import org.dromara.reader.domain.vo.admin.ReaderBatchActionResult;

import java.util.List;

/** 书源采集中心管理服务。 */
public interface IReaderSourceService {

    PageResult<ReaderSourceSite> querySitePage(ReaderSourceSiteQueryBo bo, PageQuery pageQuery);

    Long saveSite(ReaderSourceSiteBo bo);

    void checkCompliance(Long siteId, ReaderSourceComplianceBo bo);

    void updateSiteStatus(Long siteId, boolean enabled);

    PageResult<ReaderSourcePolicy> queryPolicyPage(String policyName, String status, PageQuery pageQuery);

    Long savePolicy(ReaderSourcePolicyBo bo);

    /** 批量启用或停用限流策略。 */
    ReaderBatchActionResult batchPolicyStatus(List<Long> policyIds, boolean enabled);

    PageResult<ReaderSourceRule> queryRulePage(Long siteId, String status, PageQuery pageQuery);

    Long saveRule(ReaderSourceRuleBo bo);

    void updateRuleStatus(Long ruleId, boolean enabled);

    PageResult<ReaderSourceTask> queryTaskPage(ReaderSourceTaskQueryBo bo, PageQuery pageQuery);

    ReaderSourceTask getTask(Long taskId);

    Long saveTask(ReaderSourceTaskBo bo);

    Long startTask(Long taskId);

    void pauseTask(Long taskId);

    Long resumeTask(Long taskId);

    /** 管理员确认后重置站点熔断状态，并重新创建一次采集运行。 */
    Long retryCircuitOpenTask(Long taskId);

    /** 检查跨日恢复的每日额度任务，并为每个任务创建一次自动重试运行记录。 */
    int retryDailyLimitTasks();

    /** 扫描可自动重试的暂停任务，并创建有界续采运行。 */
    int retryRecoverableTasks();

    void cancelTask(Long taskId);

    PageResult<ReaderSourceTaskRunAdminVo> queryTaskRuns(Long taskId, PageQuery pageQuery);

    PageResult<ReaderSourceChapterSnapshot> querySnapshots(Long taskId, String status, PageQuery pageQuery);

    PageResult<ReaderSourceError> queryErrors(Long taskId, String resolved, PageQuery pageQuery);

    /** 查询任务中的书籍明细。 */
    PageResult<ReaderSourceTaskBook> queryTaskBooks(Long taskId, ReaderSourceTaskBookQueryBo bo, PageQuery pageQuery);

    /** 查询任务的备用书源路由。 */
    List<ReaderSourceTaskFallback> queryTaskFallbacks(Long taskId);

    /** 从其他已授权站点的可搜索规则自动生成备用路由。 */
    int autoProvisionTaskFallbacks(Long taskId);

    /** 查询主任务自动生成的续采子任务。 */
    List<ReaderSourceTask> queryTaskChildren(Long taskId);

    /** 保存任务的备用书源路由。 */
    Long saveTaskFallback(ReaderSourceTaskFallback fallback);

    /** 查询任务事件日志。 */
    PageResult<ReaderSourceTaskLog> queryTaskLogs(Long taskId, PageQuery pageQuery);

    /** 查询任务中的单本书明细。 */
    ReaderSourceTaskBook getTaskBook(Long taskId, Long taskBookId);

    /** 查询任务单本书对应的章节快照。 */
    PageResult<ReaderSourceChapterSnapshot> queryTaskBookSnapshots(Long taskId, Long taskBookId, PageQuery pageQuery);
}
