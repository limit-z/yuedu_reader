-- 书源访问策略安全加固
-- Retry-After 是站点返回的退避信号，不允许通过旧配置或管理端关闭。
UPDATE reader_source_policy
SET honor_retry_after = '1'
WHERE honor_retry_after IS NULL OR honor_retry_after <> '1';
