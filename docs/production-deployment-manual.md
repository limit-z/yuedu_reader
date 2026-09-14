# 悦读 Reader 生产部署手册

本文针对当前工作区的三个项目：

- 后端：`/Users/yabin/code/ruoyi/RuoYi-Vue-Plus`
- 管理端：`/Users/yabin/code/ruoyi/plus-ui`
- 读者端：`/Users/yabin/code/ruoyi/reader-uniapp`

推荐基线是“一台 Linux 应用服务器 + MySQL + Redis + MinIO + Nginx + systemd”。数据库、Redis 和对象存储也可以放在云服务，但应用服务器必须能通过内网访问它们。本文以 MySQL 8、Redis 7/8、MinIO、JDK 21、Node.js 20.19+、pnpm 10、Maven Wrapper 为准。

> 当前仓库的 `script/docker/docker-compose.yml` 是开发/演示编排：使用默认密码、`network_mode: host`、固定 `/docker/...` 宿主机路径、预构建镜像和双后端实例。不要未经修改直接用于公网生产。

## 1. 部署拓扑

```text
浏览器/H5/小程序
        |
     HTTPS 443
        |
      Nginx
   /             \
静态前端       /prod-api/ -> 127.0.0.1:8080
                     |
             ruoyi-admin.jar
                |       \
             MySQL     Redis
                |
              MinIO/OSS
```

生产只开放 Nginx 的 `80/443`。MySQL、Redis、MinIO API、Actuator 和 Java `8080` 只允许应用内网或本机访问；MinIO 控制台 `9001`、SnailJob 等管理端口只允许运维 IP。

## 2. 组件与域名规划

可以使用一个域名，也可以拆分域名。单域名方案最简单：

| 用途 | 地址 | 说明 |
| --- | --- | --- |
| 管理端 | `https://admin.example.com/` | `plus-ui/dist` 静态文件 |
| 读者 H5 | `https://read.example.com/` | `reader-uniapp/dist/build/h5` 静态文件 |
| 后端 API | 同 H5 或独立域名 | Nginx 转发到 `/prod-api/` 或 `/` |
| MinIO 公共资源 | `https://oss.example.com/` | 推荐使用云 OSS/CDN；自建 MinIO 时单独代理 |

本文示例使用：

```text
ADMIN_DOMAIN=admin.example.com
READER_DOMAIN=read.example.com
API_ORIGIN=https://read.example.com
APP_DIR=/opt/ruoyi
```

不要把示例域名、密码、密钥直接用于生产。

## 3. 服务器准备

### 3.1 系统账户和目录

以 Ubuntu/Debian 为例，创建不可登录的应用用户，并准备发布目录：

```bash
sudo useradd --system --home /opt/ruoyi --shell /usr/sbin/nologin ruoyi
sudo mkdir -p /opt/ruoyi/{releases,current,logs,data/reader-covers,temp,frontend/admin,frontend/reader}
sudo chown -R ruoyi:ruoyi /opt/ruoyi
```

建议至少预留 4 vCPU、8 GB 内存、100 GB SSD；章节正文、封面、MinIO 数据和日志增长较快时按实际容量扩展。配置防火墙只放行 `22`（限定运维 IP）、`80`、`443`。

### 3.2 安装运行时

```bash
java -version          # 必须为 21
mvn -version           # 构建机需要 Maven Wrapper 可访问网络
node -v                # >= 20.19
pnpm -v                # >= 10
mysql --version        # MySQL 客户端即可，数据库可为云服务
redis-cli --version
nginx -v
```

后端构建使用仓库的 `./mvnw`，不要求服务器安装 Maven；前端构建使用 `pnpm install --frozen-lockfile`。如果构建和运行分离，生产服务器只需要 JRE/JDK 21、Nginx 和客户端工具。

## 4. 数据库、Redis、MinIO

### 4.1 MySQL

创建独立数据库用户，禁止应用使用 root：

```sql
CREATE DATABASE `ry-vue` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'ruoyi_app'@'10.%' IDENTIFIED BY '替换为高强度密码';
GRANT ALL PRIVILEGES ON `ry-vue`.* TO 'ruoyi_app'@'10.%';
FLUSH PRIVILEGES;
```

把仓库固定在同一版本/提交后，按以下顺序导入 MySQL 脚本。首次部署只执行一次：

```bash
cd /opt/ruoyi/source/RuoYi-Vue-Plus
mysql -h DB_HOST -P 3306 -u root -p --default-character-set=utf8mb4 ry-vue < script/sql/ry_vue.sql
mysql -h DB_HOST -P 3306 -u root -p --default-character-set=utf8mb4 ry-vue < script/sql/ry_reader.sql
```

按启用的模块追加：

```bash
# 使用 SnailJob/定时任务时执行
mysql -h DB_HOST -P 3306 -u root -p --default-character-set=utf8mb4 ry-vue < script/sql/ry_job.sql

# 使用工作流时执行
mysql -h DB_HOST -P 3306 -u root -p --default-character-set=utf8mb4 ry-vue < script/sql/ry_workflow.sql

# 使用 AI 模块时执行；不启用 AI 可不执行
mysql -h DB_HOST -P 3306 -u root -p --default-character-set=utf8mb4 ry-vue < script/sql/ry_ai.sql
```

`script/sql/oracle`、`postgres`、`sqlserver` 是其他数据库方言，不能和 MySQL 脚本混用。`script/sql/upgrade` 是增量升级脚本，不是初始化脚本。升级前先备份，并按文件名日期从旧到新执行；每次记录执行时间、Git 提交和执行结果。

```bash
mysqldump -h DB_HOST -P 3306 -u root -p --single-transaction --routines --events ry-vue \
  | gzip > /opt/backup/ry-vue-$(date +%F-%H%M%S).sql.gz

for sql in script/sql/upgrade/*.sql; do
  echo "Applying $sql"
  mysql -h DB_HOST -P 3306 -u root -p --default-character-set=utf8mb4 ry-vue < "$sql" || exit 1
done
```

不要在没有确认版本基线时盲目执行全部升级脚本。对于已有环境，先比较表/字段，必要时在预生产库演练。

### 4.2 Redis

Redis 只监听内网或 `127.0.0.1`，必须设置密码、持久化和磁盘告警。仓库示例配置位于 `script/docker/redis/conf/redis.conf`，包含 RDB + AOF，但密码 `ruoyi123` 只能作为示例，生产必须更换。应用当前生产配置默认使用：

```text
host=localhost
port=6379
database=0
password=ruoyi123  # 必须替换
```

部署后通过环境变量覆盖，见第 6 节。确认连接：

```bash
redis-cli -h REDIS_HOST -p 6379 -a 'REDIS_PASSWORD' ping
```

### 4.3 MinIO 或云 OSS

`ry_vue.sql` 会初始化 `sys_oss_config`，其中默认启用的 MinIO 配置是 `127.0.0.1:9000`、bucket `ruoyi`、示例凭据。首次上线必须在管理端“文件配置管理”中改成真实的 endpoint、access key、secret key、bucket 和公网访问域名；不要把 MinIO 管理端口暴露给公网。

推荐生产使用云 OSS + CDN。若自建 MinIO：

1. 创建专用 root/业务账号和 bucket，关闭匿名写入。
2. 应用通过内网 endpoint 写入，`sys_oss_config` 的 URL 使用 HTTPS 公网域名或 CDN。
3. 将 MinIO `/data` 纳入备份，开启磁盘容量告警。
4. 让 `/opt/ruoyi/data/reader-covers` 持久化；这是 `reader.cover.storage-path` 的本地封面缓存，不能随发布包删除。

## 5. 获取代码和构建产物

固定发布版本，不要直接部署开发分支工作区：

```bash
sudo -u ruoyi mkdir -p /opt/ruoyi/source
cd /opt/ruoyi/source
git clone <RuoYi-Vue-Plus仓库地址> RuoYi-Vue-Plus
git clone <plus-ui仓库地址> plus-ui
git clone <reader-uniapp仓库地址> reader-uniapp
cd RuoYi-Vue-Plus
git checkout <已验证的tag或commit>
```

后端构建：

```bash
cd /opt/ruoyi/source/RuoYi-Vue-Plus
./mvnw -Pprod -DskipTests clean package
test -f ruoyi-admin/target/ruoyi-admin.jar
```

`-Pprod` 会把 Maven profile 写入 `application-prod.yml`，并将日志级别设为 `warn`。不要使用默认 `dev` profile 发布。

管理端构建：

```bash
cd /opt/ruoyi/source/plus-ui
pnpm install --frozen-lockfile
pnpm build:prod
test -f dist/index.html
```

`plus-ui/.env.production` 当前已经使用 `VITE_APP_BASE_API=/prod-api`、`VITE_APP_CONTEXT_PATH=/`。如果管理端部署到 `/admin/`，必须把 `VITE_APP_CONTEXT_PATH=/admin/`，同时修改 Nginx 的静态根路径和 SPA 回退。

读者 H5 构建：

```bash
cd /opt/ruoyi/source/reader-uniapp
cat > .env.production <<'EOF'
VITE_READER_API_BASE_URL=https://read.example.com
VITE_READER_CLIENT_ID=<生产客户端ID>
EOF
pnpm install --frozen-lockfile
pnpm build:h5
test -f dist/build/h5/index.html
# 生产包不应再出现本地/内网回退地址
! rg -n 'localhost|192\\.168\\.|127\\.0\\.0\\.1' dist/build/h5
```

不要在生产环境设置 `VITE_READER_DEV_TOKEN`。它只用于本地联调；当前 `reader-uniapp/src/api/http.ts` 在环境变量缺失时会回退到开发机内网地址，因此上面的构建后扫描必须通过。微信小程序不是 Nginx 静态部署：构建 `pnpm build:mp-weixin` 后，将 `dist/build/mp-weixin` 导入微信开发者工具，配置业务域名和合法 request/upload/download 域名，再提交审核。

仓库旁边还有独立的 `/Users/yabin/code/ruoyi/book-h5` 项目。当前架构文档把 `reader-uniapp` 定为 H5/小程序/App 的 C 端主工程，`book-h5` 不要和 `reader-uniapp` 同时作为线上读者端；若你明确选择 `book-h5`，需单独配置它的 `VITE_READER_API_BASE_URL` 并执行自己的 `npm run build`，静态根改为它的 `dist`。

发布产物：

```bash
RELEASE=/opt/ruoyi/releases/$(date +%Y%m%d%H%M%S)
sudo -u ruoyi mkdir -p "$RELEASE"/{backend,admin,reader}
sudo -u ruoyi cp /opt/ruoyi/source/RuoYi-Vue-Plus/ruoyi-admin/target/ruoyi-admin.jar "$RELEASE/backend/ruoyi-admin.jar"
sudo -u ruoyi cp -a /opt/ruoyi/source/plus-ui/dist/. "$RELEASE/admin/"
sudo -u ruoyi cp -a /opt/ruoyi/source/reader-uniapp/dist/build/h5/. "$RELEASE/reader/"
sudo -u ruoyi ln -sfn "$RELEASE" /opt/ruoyi/current
```

## 6. 后端生产配置

当前 `ruoyi-admin/src/main/resources/application-prod.yml` 中仍有 `localhost`、root/root、Redis 示例密码和默认 JWT 密钥；`application.yml` 还包含接口加密示例密钥。不要直接接受这些值。优先使用 systemd 的 `EnvironmentFile` 覆盖配置，不把密码提交到 Git。

建立权限为 `600` 的 `/etc/ruoyi/ruoyi.env`：

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL=jdbc:mysql://DB_HOST:3306/ry-vue?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8&autoReconnect=true&rewriteBatchedStatements=true&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true
SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME=ruoyi_app
SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD=替换为数据库密码

SPRING_DATA_REDIS_HOST=REDIS_HOST
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_DATABASE=0
SPRING_DATA_REDIS_PASSWORD=替换为Redis密码

SA_TOKEN_JWT_SECRET_KEY=替换为至少32位随机值
READER_SOURCE_WORKER_SECRET=替换为独立的Worker共享密钥
READER_SOURCE_ALLOW_PRIVATE_FOR_TEST=false
READER_COVER_STORAGE_PATH=/opt/ruoyi/data/reader-covers
SPRING_SERVLET_MULTIPART_LOCATION=/opt/ruoyi/temp

# 若使用外部配置中心/监控，再按实际启用；默认关闭即可
SPRING_BOOT_ADMIN_CLIENT_ENABLED=false
SNAIL_JOB_ENABLED=false
SNAIL_AI_ENABLED=false
```

`SA_TOKEN_JWT_SECRET_KEY` 是 Spring relaxed binding 对 `sa-token.jwt-secret-key` 的环境变量写法。生产环境还应在构建前/发布前更换 `application.yml` 中的接口加密 RSA 公私钥，并同步更新 `plus-ui/.env.production` 的 `VITE_APP_RSA_PUBLIC_KEY`、`VITE_APP_RSA_PRIVATE_KEY`；前后端必须成对更换，不能只改一侧。若暂时不更换，至少确认这些密钥未被当作长期安全凭据使用，并限制仓库和构建日志访问。

写入并保护文件：

```bash
sudo chown root:ruoyi /etc/ruoyi/ruoyi.env
sudo chmod 600 /etc/ruoyi/ruoyi.env
```

生产配置还要检查：

- `management.endpoints.web.exposure.include` 当前是 `*`，必须依靠 Nginx/防火墙阻断公网 Actuator；不要把 `/actuator/env`、`/actuator/beans` 暴露给互联网。
- `application-prod.yml` 默认把上传临时目录写成 `/ruoyi/server/temp`；示例 `EnvironmentFile` 已覆盖为 `/opt/ruoyi/temp`，否则 systemd 用户可能无权写入。
- `springdoc.api-docs.enabled` 当前为 `true`，公网不需要时关闭，或仅允许内网访问。
- `message.allowedOrigins` 当前为 `*`，上线后改成真实前端来源。
- `justauth.address` 必须改成真实 HTTPS 地址，第三方 OAuth 回调地址同步修改。
- 上传大小、日志级别、CORS、Cookie、邮件/短信、OSS 域名按实际业务改值。

## 7. systemd 管理后端

创建 `/etc/systemd/system/ruoyi.service`：

```ini
[Unit]
Description=RuoYi Reader backend
After=network-online.target
Wants=network-online.target

[Service]
User=ruoyi
Group=ruoyi
WorkingDirectory=/opt/ruoyi
EnvironmentFile=/etc/ruoyi/ruoyi.env
ExecStart=/usr/bin/java -Duser.timezone=Asia/Shanghai -Xms512m -Xmx2g -XX:+HeapDumpOnOutOfMemoryError -XX:+UseZGC -jar /opt/ruoyi/current/backend/ruoyi-admin.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
TimeoutStopSec=60
LimitNOFILE=65535
ReadWritePaths=/opt/ruoyi/logs /opt/ruoyi/data /opt/ruoyi/temp
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

启动并检查：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now ruoyi
sudo systemctl status ruoyi --no-pager
journalctl -u ruoyi -f
ss -lntp | grep ':8080'
```

仓库的 `script/bin/ry.sh` 只适合快速手工启停：它依赖当前目录、通过 `ps | grep` 找进程、把标准输出丢到 `/dev/null`，没有健康检查，也会一直等待进程退出。生产建议 systemd；若必须使用脚本，应在包含 `ruoyi-admin.jar` 和 `logs` 的目录执行，并先改进日志、PID、JVM 内存和退出超时。

## 8. Nginx 和 HTTPS

把管理端和读者端分别部署到静态目录，并配置 SPA 回退。以下为单域名 API 方案的核心配置；若使用两个域名，复制一个 `server` 块即可：

```nginx
server {
    listen 80;
    server_name admin.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name admin.example.com;
    ssl_certificate /etc/letsencrypt/live/admin.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/admin.example.com/privkey.pem;
    client_max_body_size 100m;

    root /opt/ruoyi/current/admin;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /prod-api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_http_version 1.1;
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 86400s;
        proxy_buffering off;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # reader-uniapp 使用绝对路径 /reader/app/...，不带 /prod-api 前缀
    location /reader/ {
        # 不带 URI 的 proxy_pass 保留 /reader/ 前缀，后端 Controller 才能匹配
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 86400s;
        proxy_buffering off;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # OSS 上传、下载和消息 SSE 也由后端处理
    location /resource/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 86400s;
        proxy_buffering off;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    location ~ ^/prod-api/actuator { return 403; }
}
```

读者域名的静态根改为 `/opt/ruoyi/current/reader`，并保留上面的 `/reader/`、`/resource/` 代理；`reader-uniapp` 的接口路径是 `/reader/app/...`，不会自动加 `/prod-api`。读者域名可以复用同一个后端代理片段，配置至少如下：

```nginx
server {
    listen 443 ssl http2;
    server_name read.example.com;
    ssl_certificate /etc/letsencrypt/live/read.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/read.example.com/privkey.pem;
    client_max_body_size 100m;
    root /opt/ruoyi/current/reader;
    index index.html;

    location / { try_files $uri $uri/ /index.html; }
    location /reader/ { proxy_pass http://127.0.0.1:8080; proxy_http_version 1.1; proxy_set_header Host $http_host; proxy_set_header X-Real-IP $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; proxy_set_header X-Forwarded-Proto $scheme; proxy_read_timeout 86400s; proxy_buffering off; }
    location /resource/ { proxy_pass http://127.0.0.1:8080; proxy_http_version 1.1; proxy_set_header Host $http_host; proxy_set_header X-Real-IP $remote_addr; proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; proxy_set_header X-Forwarded-Proto $scheme; proxy_read_timeout 86400s; proxy_buffering off; }
}
```

若改用独立 API 域名，则在该域名的 Nginx 中代理 `/reader/`、`/resource/` 和管理端需要的 `/prod-api/`。注意 `/prod-api/` 的 `proxy_pass http://127.0.0.1:8080/` 会去掉前缀；`/reader/`、`/resource/` 必须使用不带结尾 URI 的写法保留前缀。

```bash
sudo nginx -t
sudo systemctl reload nginx
```

使用 Certbot/云证书配置 HTTPS，强制 HTTP 跳转 HTTPS；小程序的 request、upload、download 合法域名必须使用 HTTPS，不能使用 IP 或 `localhost`。SSE/长连接必须保留 `proxy_http_version 1.1`、`proxy_buffering off` 和足够大的 `proxy_read_timeout`，仓库自带 Nginx 示例已包含这些要点。

## 9. 首次上线验收

按顺序验证：

```bash
curl -I https://admin.example.com/
curl -I https://read.example.com/
curl -i https://admin.example.com/prod-api/actuator/health
curl -sS https://admin.example.com/prod-api/reader/app/home
```

Actuator 不应从公网返回详细信息；健康检查可以改为只允许内网或在 Nginx 另设受保护路径。浏览器验收至少覆盖：管理员登录、验证码、OSS 上传、阅读器首页/分类/详情/目录/正文、游客访问、登录后书架/历史/进度、评论/反馈，以及 SSE 消息连接。

若使用仓库的联调检查脚本，它不是生产部署脚本，且路径写死为本地开发目录、端口 `8080/5173/5175`，必须先改成服务器参数；运行前要设置 `ACTUATOR_PASSWORD`。生产 Worker 只运行已授权书源，配置 `READER_SOURCE_API_BASE_URL`、`READER_SOURCE_WORKER_SECRET`、`READER_SOURCE_WORKER_ID`，保持 `READER_SOURCE_ALLOW_PRIVATE_FOR_TEST` 为 `false` 或未设置。

## 10. Worker 部署（可选）

采集中心没有自动启动 Worker。根据语言选择一种，建议单独机器/容器运行：

```bash
cd /opt/ruoyi/source/RuoYi-Vue-Plus/tools/reader-source-worker/python
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
export READER_SOURCE_API_BASE_URL=https://read.example.com
export READER_SOURCE_WORKER_SECRET='与后端一致的密钥'
export READER_SOURCE_WORKER_ID=python-worker-01
python reader_source_worker.py
```

Worker 只访问管理员明确授权的站点；不要启用 `READER_SOURCE_ALLOW_PRIVATE_FOR_TEST=true`，不要把 Worker 共享密钥写入仓库或命令历史。生产应为 Worker 配置 systemd、日志轮转、资源限制和故障告警。

## 11. 脚本解读和使用边界

| 脚本/目录 | 作用 | 生产建议 |
| --- | --- | --- |
| `script/bin/ry.sh` | Linux 手工启动/停止/重启/状态，固定寻找 `ruoyi-admin.jar` | 不建议作为最终进程管理，改用 systemd |
| `script/bin/ry.bat` | Windows 交互式启停 | 仅开发或 Windows 服务器，不用于 Linux |
| `reader-smoke-precheck.sh` | 检查本地目录、JDK、端口、健康接口、前端环境变量 | 联调前检查；路径和端口写死，不能直接当生产探针 |
| `reader-fix-remote-schema.sh` | 通过 PyMySQL 修复 `reader_*` 字符集并补字段 | 只在确认远端 schema 漂移时执行；先备份，需 Python + PyMySQL |
| `reader-seed-remote-demo-data.sh` | 写入访客、反馈、公告、专题、消息等演示数据 | 仅测试/演示；不建议在线上真实库执行 |
| `reader-reset-remote-test-data.sh --reader-only` | 删除阅读器关联 OSS 文件/记录并清空 `reader_*` 表 | 高危清库脚本，禁止生产使用 |
| `reader-reset-remote-test-data.sh --all-bucket` | 清空整个 MinIO bucket、删除全部 MinIO OSS 记录并清空阅读器表 | 极高危，只能在临时测试库确认后使用 |
| `reader-import-e2e.js` | 登录、OSS 上传、导入和阅读器端到端验证，依赖本机路径/Redis/MySQL | 测试脚本，执行前审查账号和文件路径，勿把 Token 放入日志 |
| `script/docker/docker-compose.yml` | MySQL、Nginx、Redis、MinIO、后端/监控/Snail 服务示例编排 | 需重写密码、镜像、网络、卷、健康检查和 secrets 后才能生产化 |
| `script/docker/database.yml` | Oracle、SQL Server、PostgreSQL 测试容器 | 文件注释已明确“正式环境需自行安装数据库” |
| `script/docker/nginx/conf/nginx.conf` | 官方示例 Nginx，含双后端 upstream、SPA、SSE | 改域名、证书、静态根和后端实例；不要直接暴露 Actuator |
| `script/docker/redis/conf/redis.conf` | Redis 密码和 RDB/AOF 示例 | 改密码、绑定地址、权限、备份和告警 |
| `script/sql/ry_vue.sql` | RuoYi 系统、菜单、OSS 配置等 MySQL 初始化 | 新库首个执行；默认数据含示例凭据，必须改 |
| `script/sql/ry_reader.sql` | Reader 作品、章节、书架、采集、审核等表 | 在 `ry_vue.sql` 后执行 |
| `script/sql/ry_job.sql` | SnailJob 表 | 启用 SnailJob 时执行 |
| `script/sql/ry_workflow.sql` | Warm-Flow 工作流表 | 启用工作流时执行 |
| `script/sql/ry_ai.sql` | AI 模块表 | 启用 AI 时执行 |
| `script/sql/upgrade/*.sql` | 按日期累积的增量结构/数据升级 | 备份、预演、按日期顺序执行并留记录 |

脚本的已知限制：

- `reader-fix-remote-schema.sh` 虽然支持 `READER_DB_NAME`，但内部查询 `information_schema.tables/columns` 时把 schema 写成了字面量 `reader`；如果线上数据库不是这个名字，字符集转换和字段补齐可能被跳过。执行前应审查脚本并修正为环境变量对应的库名。
- `reader-import-e2e.js` 的 MySQL 驱动 JAR 和导入文本默认是开发机绝对路径；它还会读取验证码和登录态，必须在测试环境改路径、改账号并保护输出，不能作为发布后自动任务。
- `reader-smoke-precheck.sh` 的后端/前端目录均是 `/Users/yabin/code/ruoyi/...`，端口固定为 `8080/5173/5175`，且依赖 macOS 的 `/usr/libexec/java_home`；Linux 服务器不能原样执行。

## 12. 版本升级和回滚

1. 在预生产用同一 Git commit、同一数据库快照和同一前端环境变量构建。
2. 备份 MySQL、Redis（必要时）、MinIO 和 `data/reader-covers`。
3. 先停写或进入维护窗口，执行对应 `script/sql/upgrade/*.sql`。
4. 发布新的 JAR 和静态目录到新时间戳 `releases`，切换 `current`。
5. `systemctl restart ruoyi`，检查日志、健康和核心业务。
6. 确认无误后保留上一版本；异常时切回旧 `current` 并重启。数据库结构回滚不能依靠切回 JAR，必须使用已验证的反向脚本或恢复数据库快照。

```bash
sudo systemctl stop ruoyi
sudo systemctl start ruoyi
sudo journalctl -u ruoyi --since '10 minutes ago' --no-pager
```

不要删除旧 release、日志、封面缓存和 OSS 对象，直到回滚窗口结束并完成备份校验。

## 13. 备份、监控和排障

- MySQL：每日全量 + binlog/PITR，定期恢复演练；备份文件加密并异地保存。
- Redis：RDB/AOF 只作为缓存/会话恢复手段，不能替代 MySQL 备份。
- MinIO/OSS：开启版本或生命周期策略，备份对象和 `sys_oss` 元数据。
- 日志：`journalctl -u ruoyi`、`/opt/ruoyi/logs`、Nginx access/error log，配置 logrotate。
- 监控：CPU、内存、磁盘、JVM heap、GC、连接池、MySQL 慢查询、Redis 内存、HTTP 5xx、SSE 连接数和 Worker 失败率。
- 安全：定期轮换数据库/Redis/MinIO/Worker 密钥，删除默认管理员和演示账号，限制 Actuator/API 文档/MinIO 控制台来源。

常见问题定位：

| 现象 | 先检查 |
| --- | --- |
| 502/后端启动失败 | `systemctl status ruoyi`、`journalctl -u ruoyi`、`ss -lntp :8080`、数据库/Redis DNS 与密码 |
| 登录验证码失败 | Redis 连通性、数据库表、前端与后端 RSA 配置是否成对 |
| 上传成功但图片 403/打不开 | `sys_oss_config` endpoint/bucket/权限、MinIO 公网 URL、Nginx/HTTPS |
| 前端刷新 404 | Nginx `try_files ... /index.html`、Vite `VITE_APP_CONTEXT_PATH` |
| 读者端请求 localhost | 重新设置 `VITE_READER_API_BASE_URL` 后执行生产构建，不能只改运行时 Nginx |
| SSE 断开或无消息 | Nginx `proxy_buffering off`、HTTP/1.1、`proxy_read_timeout`、前端来源白名单 |
| 采集任务不推进 | Worker 共享密钥、API 地址、Worker ID、Redis 限流、授权书源和租约日志 |

## 14. 上线前最终检查清单

- [ ] 使用固定 Git commit/tag，构建日志和产物可追溯。
- [ ] MySQL 初始化/升级脚本在预生产成功执行并有备份。
- [ ] MySQL 不使用 root；Redis、MinIO、Worker 密钥已更换。
- [ ] `SA_TOKEN_JWT_SECRET_KEY`、RSA 密钥和 OAuth 回调已按生产域名配置。
- [ ] `VITE_READER_DEV_TOKEN` 未进入生产包或环境变量。
- [ ] 后端、Redis、MySQL、MinIO、Actuator 管理端口未暴露公网。
- [ ] 管理端、读者 H5、API、OSS 均通过 HTTPS。
- [ ] Nginx SPA 回退、上传大小、SSE 长连接已验证。
- [ ] `reader_*`、系统表、OSS 对象、封面缓存均已纳入备份。
- [ ] 已完成管理员登录、导入、审核、发布、阅读、书架、进度、反馈和回滚演练。
- [ ] `reader-reset-remote-test-data.sh`、演示数据脚本未在线上执行。
