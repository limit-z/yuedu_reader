# Reader P0 Smoke Precheck Report

> 检查时间：2026-08-12
> 检查目的：确认本地是否满足第一次真实 P0 联调冒烟的基础条件
> 检查脚本：`/Users/yabin/code/ruoyi/RuoYi-Vue-Plus/script/bin/reader-smoke-precheck.sh`

## 1. 当前结论

当前环境**暂不满足**第一次真实 P0 联调冒烟条件。

已经确认：

- `JDK 21` 已安装可用
- 三个代码仓目录都存在
- `pnpm`、`curl` 可用
- 项目自带了 MySQL/Redis 的 Docker 编排文件

当前主要阻塞：

- 本机没有检测到 `3306` 的 MySQL 监听
- 本机没有检测到 `6379` 的 Redis 监听
- 本机没有检测到 `8080` 的后端监听
- 本机没有检测到 `5173` 的 `plus-ui` 开发服务监听
- 本机没有检测到 `5175` 的 `reader-uniapp H5` 开发服务监听
- `reader-uniapp` 本地联调环境变量文件尚未创建
- 当前系统未检测到 `docker` 命令

## 2. 检查摘要

| 分类 | 结果 |
| --- | --- |
| PASS | 8 |
| WARN | 5 |
| FAIL | 8 |

## 3. 通过项

- 后端仓库目录存在
- `plus-ui` 仓库目录存在
- `reader-uniapp` 仓库目录存在
- `java` 命令可用
- `pnpm` 命令可用
- `curl` 命令可用
- 已检测到 `JDK 21`
- 已发现项目自带 Docker 编排文件

## 4. 警告项

- 当前系统未检测到 `docker` 命令
- 未发现 `reader-uniapp` 的本地环境变量文件
- `VITE_READER_API_BASE_URL` 未配置
- `VITE_READER_CLIENT_ID` 未配置
- `VITE_READER_DEV_TOKEN` 未配置

## 5. 失败项

- MySQL 端口 `3306` 未监听
- Redis 端口 `6379` 未监听
- RuoYi 后端端口 `8080` 未监听
- `plus-ui` 开发服务端口 `5173` 未监听
- `reader-uniapp H5` 开发服务端口 `5175` 未监听
- 后端健康检查 `http://localhost:8080/actuator/health` 不可达
- `plus-ui` 首页 `http://localhost:5173/` 不可达
- `reader-uniapp H5` 首页 `http://localhost:5175/` 不可达

## 6. 下一步建议顺序

1. 准备或启动 MySQL。
2. 准备或启动 Redis。
3. 创建 `reader-uniapp` 本地环境变量文件。
4. 填写：
   `VITE_READER_API_BASE_URL`
   `VITE_READER_CLIENT_ID`
   `VITE_READER_DEV_TOKEN`
5. 启动后端服务。
6. 启动 `plus-ui`。
7. 启动 `reader-uniapp H5`。
8. 重新执行前置检查脚本。
9. 前置检查通过后，再进入第一次真实 P0 联调冒烟。

## 7. 复查命令

```bash
/Users/yabin/code/ruoyi/RuoYi-Vue-Plus/script/bin/reader-smoke-precheck.sh
```
