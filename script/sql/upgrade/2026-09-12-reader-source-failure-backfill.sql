-- 为历史采集任务补齐可观测失败分类，并让可恢复异常进入一次受控自动重试。
-- 幂等：只填充 failure_code 为空的历史任务，不修改已完成任务、章节正文或审核数据。

UPDATE reader_source_task
SET failure_code = CASE
        WHEN fail_reason LIKE '%每日请求%' OR fail_reason LIKE '%额度%' THEN 'DAILY_LIMIT'
        WHEN fail_reason LIKE '%403%' THEN 'HTTP_403'
        WHEN fail_reason LIKE '%401%' THEN 'HTTP_401'
        WHEN fail_reason LIKE '%approved host%' OR fail_reason LIKE '%SSRF%' THEN 'SSRF'
        WHEN fail_reason LIKE '%超时%' OR fail_reason LIKE '%timed out%' OR fail_reason LIKE '%handshake%' THEN 'TIMEOUT'
        WHEN fail_reason LIKE '%哈希校验%' OR fail_reason LIKE '%正文质量%' THEN 'QUALITY'
        WHEN fail_reason LIKE '%章节序号%' OR fail_reason LIKE '%目录%' OR fail_reason LIKE '%chapters%' THEN 'PARSE'
        WHEN fail_reason LIKE '%熔断%' THEN 'CIRCUIT_OPEN'
        ELSE 'UNKNOWN'
    END
WHERE (failure_code IS NULL OR failure_code = '')
  AND status IN ('PAUSED', 'FAILED')
  AND fail_reason IS NOT NULL AND fail_reason <> '';

-- 旧版本把 5xx 回执保存为通用 HTTP；按错误明细恢复为可读且可重试的分类。
UPDATE reader_source_task t
JOIN (
    SELECT e.task_id, MAX(e.http_status) AS http_status
    FROM reader_source_error e
    WHERE e.error_type = 'HTTP' AND e.http_status BETWEEN 500 AND 599
    GROUP BY e.task_id
) e ON e.task_id = t.id
SET t.failure_code = 'HTTP_5XX'
WHERE t.failure_code = 'HTTP';

-- 可恢复历史异常从当前时间开始排队一次；授权、安全和每日额度异常不会自动重试。
UPDATE reader_source_task
SET retry_after = NOW(),
    fail_reason = CONCAT('等待自动重试（历史故障回填）：', LEFT(fail_reason, 700))
WHERE status = 'PAUSED'
  AND auto_retry_enabled = '1'
  AND auto_retry_count < COALESCE(max_auto_retry_count, 3)
  AND failure_code IN ('TIMEOUT', 'QUALITY', 'PARSE', 'UNKNOWN')
  AND retry_after IS NULL;

-- 给历史任务留下迁移事件，便于管理端明确区分“新采集日志”和“历史补录日志”。
INSERT INTO reader_source_task_log
    (task_id, level, event_type, message, detail_json, event_at, create_time, update_time)
SELECT t.id, 'INFO', 'TASK', '历史任务失败分类已回填，后续按分类策略处理',
       JSON_OBJECT('failureCode', t.failure_code, 'retryScheduled', IF(t.retry_after IS NULL, FALSE, TRUE)),
       NOW(), NOW(), NOW()
FROM reader_source_task t
WHERE t.failure_code IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM reader_source_task_log l
      WHERE l.task_id = t.id AND l.event_type = 'TASK'
        AND l.message = '历史任务失败分类已回填，后续按分类策略处理'
  );
