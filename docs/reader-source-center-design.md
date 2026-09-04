# 书源采集中心设计方案

版本：V1.0
状态：第一阶段设计基线
适用端：RuoYi 管理端、reader 业务后端、Java/Python/Go 采集执行器

## 1. 目标与边界

书源采集中心负责维护“允许采集的网站、解析规则、访问策略、采集任务、运行记录和章节差异”，将采集结果交给阅读器现有的作品、章节、正文和审核链路。

本中心不是通用的反爬绕过平台。只有在内容版权、站点服务条款和 robots.txt 允许的前提下，才允许执行任务。系统必须拒绝以下能力：代理池/IP 轮换、验证码绕过、浏览器指纹伪装、隐藏身份的请求头伪造、突破登录或付费墙、绕过封禁以及抓取明确禁止的内容。

采集执行器不直接写业务库，只返回统一的结构化结果；Java 编排器负责鉴权、限流、租约、幂等、差异计算、审核入库和发布。这样可以并行支持 Java、Python、Go，同时保持业务数据入口唯一。

## 2. 总体架构

```text
Web 管理端
    |
    v
Java Source Orchestrator
    |-- 站点/规则/策略校验
    |-- Redis 租约、限流、熔断、幂等
    |-- Worker 调度与心跳
    |-- 结果校验、差异计算、审核入库
    |
    +-- Java Worker：轻量规则、同 JVM 部署
    +-- Python Worker：HTML/JSON 解析、复杂文本清洗
    +-- Go Worker：长任务、低资源占用、独立部署
    |
    v
统一采集结果协议 -> reader_work / reader_novel_chapter / reader_novel_chapter_content
                  -> reader_content_audit -> 人工审核 -> 发布缓存
```

Worker 通过 HTTP 或消息队列领取任务，使用一次性 `runToken` 回传结果。生产环境建议使用队列；第一阶段先实现 HTTP 适配器和持久化状态机，避免引入新的基础设施依赖。

## 3. 领域模型

### 3.1 书源站点 `reader_source_site`

保存站点名称、根域名、授权说明、robots/条款检查结果、启停状态和默认策略。域名必须经过 URL 解析和主机白名单校验，不能指向 localhost、内网、保留地址或云元数据地址。

### 3.2 解析规则 `reader_source_rule`

规则按站点维护，定义搜索页、详情页、目录页、章节页的 URL 模板和字段选择器。选择器只允许声明式 CSS/XPath/JSONPath，不执行站点返回的脚本，不允许在服务端 `eval` 任意表达式。规则保存版本号，任务运行时固定规则版本，便于回溯。

### 3.3 访问策略 `reader_source_policy`

每站点可以绑定一套策略：单域名并发数、最小/最大间隔、每分钟请求上限、每日上限、连接/读取超时、重试次数、`Retry-After` 是否生效、失败熔断阈值和人工确认开关。默认值是并发 1、间隔 3-8 秒、遇到 429 遵守 `Retry-After`。

### 3.4 采集任务 `reader_source_task`

任务是一次可暂停、恢复、取消的业务计划，保存书源、规则版本、策略版本、执行器类型、目标作品、起止章节、增量模式和当前游标。任务只能进入明确的状态机，不允许通过直接改库跳过审核。

### 3.5 运行记录 `reader_source_task_run`

每次启动任务生成运行记录，保存租约、心跳、请求数、成功数、跳过数、失败数、429 数、耗时、熔断原因和结果摘要。运行记录是监控和审计主依据。

### 3.6 章节快照与错误

章节快照保存来源 URL、来源章节标识、内容哈希、标题哈希和采集时间，用于增量判断和人工差异对比。错误表保存脱敏后的 HTTP 状态、错误分类、重试时间和错误摘要，不记录 Cookie、Authorization、完整请求头或正文中的敏感数据。

## 4. 任务状态机

```text
DRAFT -> READY -> RUNNING -> PAUSED -> RUNNING
                   |            |
                   v            v
             WAITING_REVIEW   CANCELED
                   |
                   v
                COMPLETED

任意运行态 -> FAILED（超过重试或触发熔断）
```

`401/403` 默认立即暂停并要求人工复核；`429` 读取 `Retry-After`，没有该响应头时使用指数退避且不超过站点策略；连续连接失败、解析失败或内容质量不达标时触发熔断。采集结果默认进入待审核，不自动发布。

## 5. 统一 Worker 协议

任务领取：

```text
POST /reader/worker/source/runs/claim
Header: X-Reader-Worker-Secret: ${READER_SOURCE_WORKER_SECRET}
Body: {"executorType":"PYTHON","workerId":"python-worker-01"}
```

Java 编排器只会下发已由管理员启动、且执行器类型匹配的运行记录。Redis 租约默认 90 秒，Worker 必须定期发送心跳；租约失效后，原 Worker 不能继续回传结果。

```json
{
  "runId": 1001,
  "taskId": 10,
  "siteId": 3,
  "ruleId": 20,
  "ruleVersion": 4,
  "runToken": "短期运行令牌",
  "workerId": "python-worker-01",
  "executorType": "PYTHON",
  "sourceWorkUrl": "https://allowed.example/book/1",
  "catalogUrlTemplate": "https://allowed.example/book/{id}",
  "chapterUrlTemplate": "https://allowed.example/chapter/{id}",
  "selectorJson": {"catalog":{"item":".chapter-item","title":".chapter-title"},"chapter":{"title":"h1","content":".content"}},
  "cursorChapterNo": 20,
  "policy": {"concurrencyLimit": 1, "minDelayMs": 3000, "maxDelayMs": 8000, "requestsPerMinute": 10}
}
```

Worker 在每次访问外部站点前必须申请访问许可：

```text
POST /reader/worker/source/runs/{runId}/permit
POST /reader/worker/source/runs/{runId}/heartbeat
POST /reader/worker/source/runs/{runId}/result
POST /reader/worker/source/runs/{runId}/error
```

任务下发结果：

```json
{
  "runId": 1001,
  "taskId": 10,
  "siteId": 3,
  "ruleId": 20,
  "ruleVersion": 4,
  "runToken": "短期运行令牌",
  "executorType": "PYTHON",
  "work": {"title": "示例作品", "sourceUrl": "https://allowed.example/book/1"},
  "cursor": {"chapterNo": 20},
  "policy": {"concurrencyLimit": 1, "minDelayMs": 3000, "maxDelayMs": 8000}
}
```

结果回传：

```json
{
  "runId": 1001,
  "runToken": "短期运行令牌",
  "workerId": "python-worker-01",
  "executorType": "PYTHON",
  "batchId": "batch-21",
  "ruleVersion": 4,
  "executor": "PYTHON",
  "items": [{
    "sourceChapterId": "ch-21",
    "sourceUrl": "https://allowed.example/book/1/ch-21",
    "chapterNo": 21,
    "chapterName": "第二十一章",
    "content": "正文……",
    "contentHash": "sha256:..."
  }],
  "cursorChapterNo": 21,
  "completed": false,
  "metrics": {"requests": 2, "success": 2, "status429": 0}
}
```

编排器必须校验 `runId`、运行令牌、Worker 租约、规则版本、内容大小上限、章节序号范围、URL 主机和哈希格式；重复的 `runId + batchId` 或 `runId + sourceChapterId + contentHash` 只确认不重复入库。

## 6. 限流与安全策略

1. 创建或启用站点前执行 robots.txt 和服务条款检查，记录检查时间、结果和人工确认人。
2. 站点级并发上限默认 1；同域名使用 Redis 分布式令牌桶或漏桶，实例重启不丢失窗口计数。
3. 请求前检查租约和熔断状态；请求后按状态码、响应头和耗时更新统计。
4. 只跟随同站点、明确允许的有限跳转；禁止 SSRF，解析 DNS 后拒绝环回、私网、链路本地和保留地址。
5. 请求超时、响应大小、单章节正文大小和任务总量都必须有限制；解析器不能执行外部脚本。
6. 日志只保存站点 ID、任务 ID、状态码、耗时和脱敏 URL；凭据使用环境变量或密钥管理服务。
7. 管理端所有写操作需要管理员权限并记录操作日志；Worker 使用短期凭据和最小权限。

## 7. 管理端页面

第一阶段页面入口统一放在“阅读器管理 > 书源采集中心”：

- 书源站点：站点 CRUD、合规检查、启停、策略绑定和连通性测试。
- 解析规则：规则版本、页面类型、选择器、测试样例、启用版本和回滚。
- 限流策略：策略模板、站点覆盖值、当前窗口计数和熔断状态。
- 采集任务：创建、预览、启动、暂停、恢复、取消、执行器选择和增量范围。
- 执行记录：实时进度、心跳、请求统计、错误摘要、重试和下载结果。
- 章节差异：来源快照与当前内容并排对比，确认后生成待审核内容。

不在本中心提供“批量试探站点频率”的功能。限流规则只能来自站点公开文档、响应头、人工配置或低频健康检查，避免探测行为本身给第三方站点造成压力。

## 8. API 契约

```text
GET    /reader/admin/source/sites/list
POST   /reader/admin/source/sites
PUT    /reader/admin/source/sites/{id}
POST   /reader/admin/source/sites/{id}/check-compliance
POST   /reader/admin/source/sites/{id}/enable
POST   /reader/admin/source/sites/{id}/disable

GET    /reader/admin/source/rules/list
POST   /reader/admin/source/rules
PUT    /reader/admin/source/rules/{id}
POST   /reader/admin/source/rules/{id}/publish
POST   /reader/admin/source/rules/{id}/disable

GET    /reader/admin/source/policies/list
POST   /reader/admin/source/policies
PUT    /reader/admin/source/policies/{id}

GET    /reader/admin/source/tasks/list
POST   /reader/admin/source/tasks
GET    /reader/admin/source/tasks/{id}
POST   /reader/admin/source/tasks/{id}/start
POST   /reader/admin/source/tasks/{id}/pause
POST   /reader/admin/source/tasks/{id}/resume
POST   /reader/admin/source/tasks/{id}/cancel
GET    /reader/admin/source/tasks/{id}/runs
GET    /reader/admin/source/tasks/{id}/diffs
GET    /reader/admin/source/tasks/{id}/errors

POST   /reader/worker/source/runs/claim
POST   /reader/worker/source/runs/{runId}/permit
POST   /reader/worker/source/runs/{runId}/heartbeat
POST   /reader/worker/source/runs/{runId}/result
POST   /reader/worker/source/runs/{runId}/error
```

## 9. 分阶段交付

### P0：可配置和可审计

- 建表、实体、Mapper、站点/规则/策略/任务/运行记录 CRUD。
- 管理端列表和编辑页，任务状态和运行记录可查询。
- Redis 租约、基础限流、幂等键和熔断状态接口。
- Worker HTTP 协议、统一协议校验和 Java 编排层的租约/限流协调。

当前代码进度：站点、策略、规则、任务、运行记录的持久化模型、管理端 CRUD、合规确认、站点/规则状态流转、任务启动暂停恢复取消和运行记录查询已完成；Worker HTTP 协议、Redis 分布式租约/动态限流、章节快照入库、哈希校验、错误记录和待审核边界已完成。Python/Go 参考 Worker 已提供，正式部署仍需配置环境变量并由管理员确认站点授权。

### P1：可执行和可审核

- Python Worker 参考实现，支持声明式 CSS 规则和同一 HTTP 协议。
- Go Worker 参考实现，支持声明式 CSS 规则和同一 HTTP 协议。
- 任务启动、暂停、恢复、取消、Worker 领取和心跳。
- 章节快照、内容哈希、重复回传幂等、差异查询和待审核入库边界。
- 指标、审计日志、错误重试和站点健康状态。

### P2：多执行器和生产化

- 消息队列传输、站点策略模板、动态 `Retry-After`、配额和告警。
- 规则灰度、版本回滚、分布式 worker 调度和运行报表。
- 合规检查凭证、授权材料和数据来源追踪。

## 10. 验收标准

- 禁止未通过合规确认的站点进入 RUNNING。
- 同一站点并发与间隔在多实例下仍不超过策略配置。
- 401/403/429 行为符合状态机和 Retry-After 约定。
- Worker 重试、断点恢复、重复回传不会生成重复章节。
- 采集结果只能进入草稿/待审核，不能绕过审核直接发布。
- 站点、规则、策略、任务和运行记录均可由管理员追溯。
- Java 模块、管理端构建和协议测试全部通过，敏感配置不进入版本库。
