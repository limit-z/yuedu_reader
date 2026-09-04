# Implementation Plan: H5 v1.0 驱动的小程序与后端重构

## Overview

本次工作以 `reader-h5-demo v1.0` 为唯一产品基线，重建 `reader-uniapp` 页面实现与 `ruoyi-reader` 后端契约，确保 H5 中所有依赖服务端的功能都能在小程序和后端中形成正式闭环。

## Architecture Decisions

- 小程序继续使用 `UniApp`，以微信小程序为当前主目标，同时兼容 H5 输出。
- 后端继续基于 `RuoYi-Vue-Plus 6.X` 的阅读器模块扩展，不新拆独立服务。
- 读取频繁但运营性强的首页内容，采用“业务表 + 聚合接口”模式，不继续用纯静态硬编码。
- 游客态继续保留，并通过扩展 `reader_visitor_account` 支撑游客档案、游客反馈与后续登录合并。
- 阅读器主题、字号、亮度等本地即时设置先保留前端缓存；需要跨端同步时再通过偏好接口落库。

## Task List

### Phase 1: 基础数据层

- [x] Task 1: 扩展 `reader_visitor_account` 支撑游客资料与偏好字段
- [x] Task 2: 新增 `reader_user_feedback` 表并补齐注释、升级脚本
- [x] Task 3: 补齐 `reader_user_message`、`reader_home_notice`、`reader_home_banner`、`reader_topic`、`reader_topic_work`、`reader_reading_comment` 的结构脚本，并已执行到远端 `reader` 库

### Checkpoint: Foundation

- [x] SQL 初始化脚本与升级脚本结构清晰
- [x] 新增表/字段都具备注释

### Phase 2: 后端第一批闭环

- [x] Task 4: 补齐个人资料查询与保存接口
- [x] Task 5: 实现用户反馈提交与列表接口
- [x] Task 6: 实现管理端反馈列表、回复、状态流转接口
- [x] Task 7: 实现作品相关推荐接口

### Checkpoint: Backend Core

- [x] 单测或最小 smoke 验证通过
- [x] `GET /reader/app/me`
- [x] `PUT /reader/app/me`
- [x] `GET/POST /reader/app/feedback`
- [x] `GET /reader/app/works/{workId}/recommendations`

### Phase 3: UniApp 第一批闭环

- [x] Task 8: 新增个人资料编辑页并接入真实接口
- [x] Task 9: 重构“我的”页，使其以 H5 v1.0 为基线
- [x] Task 10: 重构反馈页并接入真实接口

### Checkpoint: First Vertical Slice

- [x] 小程序“我的” -> “个人资料编辑” -> 保存成功
- [x] 小程序“我的” -> “意见反馈” -> 提交成功
- [x] 返回“我的”页数据回显正常

### Phase 4: 首页与发现增强

- [ ] Task 11: 动态公告 / 横幅 / 专题表与接口
  - 已完成表结构与远端初始化演示数据
  - 待完成后端正式接口、plus-ui 运营配置页与 UniApp 页面接入
- [ ] Task 12: 书城 v1.0 页面重构
- [ ] Task 13: 分类 v1.0 页面重构

### Phase 5: 阅读器增强

- [ ] Task 14: 阅读器页面按 H5 v1.0 重构
- [ ] Task 15: 点评数据模型与接口
- [ ] Task 16: 阅读器设置面板与连续阅读联动
- [x] Task 17: 阅读历史返回章节、页码与完整阅读定位
  - 历史 VO 返回 `chapterNo`、`pageNo` 和 `locationValue`
  - 兼容旧的纯数字页码，支持分页与上下滚动阅读模式
  - 新增定位字段扩容升级脚本 `2026-09-03-reader-reading-location.sql`

## Latest Execution Snapshot

- 已于 2026-08-15 执行远端库升级并落地以下新表：
  - `reader_user_message`
  - `reader_home_notice`
  - `reader_home_banner`
  - `reader_topic`
  - `reader_topic_work`
  - `reader_reading_comment`
- 已于 2026-08-15 初始化首批演示数据：
  - `reader_visitor_account` 1 条
  - `reader_user_feedback` 4 条
  - `reader_user_message` 3 条
  - `reader_home_notice` 3 条
  - `reader_home_banner` 2 条
  - `reader_topic` 3 条
  - `reader_topic_work` 3 条
- 已于 2026-09-03 完成阅读历史恢复闭环：历史接口返回章节序号和完整阅读定位，H5 可从历史记录恢复到对应章节的页码、正文偏移或滚动位置；相关 reader 服务测试 5 项通过，reader 模块编译通过。

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| H5 v1.0 功能面较大 | High | 采用垂直切片，优先做“我的/反馈/推荐”闭环 |
| 游客态与登录态并存，档案逻辑易分叉 | High | 统一通过 `ReaderVisitorAccountService` 解析读者主体 |
| 首页推荐和专题配置扩展过快 | Medium | 第一版先以简单业务表支撑，不提前抽象成复杂 CMS |
| 阅读点评涉及正文选区与前后端联动 | Medium | 先设计表和接口，作为第二批实施 |

## Open Questions

- 阅读点评第一版是否先只支持小说正文，不覆盖漫画？
- 首页专题与榜单是否需要后台可配置排序权重，还是第一版先按固定规则生成？
- 个人资料中的“密码修改”是否直接复用系统用户能力，还是小程序端第一版仅保留入口占位？
