# Todo: H5 v1.0 -> UniApp + Backend

## Phase 1

- [x] 扩展 `reader_visitor_account`
- [x] 新增 `reader_user_feedback`
- [x] 补齐 SQL 注释与升级脚本
- [x] 新增 `reader_user_message`
- [x] 新增 `reader_home_notice`
- [x] 新增 `reader_home_banner`
- [x] 新增 `reader_topic`
- [x] 新增 `reader_topic_work`
- [x] 新增 `reader_reading_comment`
- [x] 执行远端库升级并初始化演示数据

## Phase 2

- [x] 补齐 `GET /reader/app/me`
- [x] 实现 `PUT /reader/app/me`
- [x] 实现 `GET /reader/app/feedback`
- [x] 实现 `POST /reader/app/feedback`
- [x] 实现 `GET /reader/admin/feedback/list`
- [x] 实现 `PUT /reader/admin/feedback/{feedbackId}/reply`
- [x] 实现 `PUT /reader/admin/feedback/{feedbackId}/status`
- [x] 实现 `GET /reader/app/works/{workId}/recommendations`

## Phase 3

- [x] 新增小程序个人资料编辑页
- [x] 小程序“我的”页接入资料接口
- [x] H5 读者个人资料完整展示与编辑页接入 `GET/PUT /reader/app/me`
- [x] H5 资料清空字段可回写，生日、地区、签名支持传空值
- [x] 小程序反馈页接入真实接口
- [x] plus-ui 管理端反馈工单页

## Phase 4

- [ ] 首页公告与横幅动态化
  - 表结构与演示数据已就绪，待后端接口与管理页接入
- [ ] 书城 v1.0 重构
- [ ] 分类 v1.0 重构

## Phase 5

- [ ] 阅读器 v1.0 重构
- [ ] 点评接口与页面
- [x] 阅读历史恢复章节、页码和完整阅读定位
- [x] `reader_reading_history.location_value` 与 `reader_reading_progress.location_value` 扩容至 `VARCHAR(512)`
