#!/usr/bin/env bash

set -u

ROOT_DIR="/Users/yabin/code/ruoyi/RuoYi-Vue-Plus"
PLUS_UI_DIR="/Users/yabin/code/ruoyi/plus-ui"
UNIAPP_DIR="/Users/yabin/code/ruoyi/reader-uniapp"
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_DB="${REDIS_DB:-10}"
ACTUATOR_USER="${ACTUATOR_USER:-ruoyi}"
ACTUATOR_PASSWORD="${ACTUATOR_PASSWORD:?请设置 ACTUATOR_PASSWORD}"

UNIAPP_ENV_FILE=""
for candidate in \
  "$UNIAPP_DIR/.env.development.local" \
  "$UNIAPP_DIR/.env.development" \
  "$UNIAPP_DIR/.env.local"
do
  if [ -f "$candidate" ]; then
    UNIAPP_ENV_FILE="$candidate"
    break
  fi
done

pass_count=0
warn_count=0
fail_count=0

print_header() {
  printf '\n== %s ==\n' "$1"
}

pass() {
  pass_count=$((pass_count + 1))
  printf '[PASS] %s\n' "$1"
}

warn() {
  warn_count=$((warn_count + 1))
  printf '[WARN] %s\n' "$1"
}

fail() {
  fail_count=$((fail_count + 1))
  printf '[FAIL] %s\n' "$1"
}

require_command() {
  local cmd="$1"
  local desc="$2"
  if command -v "$cmd" >/dev/null 2>&1; then
    pass "$desc: $(command -v "$cmd")"
    return 0
  fi
  fail "$desc: 未找到命令 $cmd"
  return 1
}

check_port() {
  local port="$1"
  local desc="$2"
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    pass "$desc: 端口 $port 正在监听"
  else
    fail "$desc: 端口 $port 未监听"
  fi
}

check_tcp() {
  local host="$1"
  local port="$2"
  local desc="$3"
  if nc -z "$host" "$port" >/dev/null 2>&1; then
    pass "$desc: $host:$port 可达"
  else
    fail "$desc: $host:$port 不可达"
  fi
}

check_http() {
  local url="$1"
  local desc="$2"
  local code
  code=$(curl -sS -o /tmp/reader-precheck-http.out -w '%{http_code}' "$url" 2>/dev/null || true)
  if [ "$code" = "200" ]; then
    pass "$desc: $url -> HTTP 200"
  else
    fail "$desc: $url -> HTTP $code"
  fi
}

read_env_value() {
  local key="$1"
  if [ -z "$UNIAPP_ENV_FILE" ]; then
    return 1
  fi
  sed -n "s/^${key}=//p" "$UNIAPP_ENV_FILE" | tail -n 1
}

print_header "1. 仓库与目录"

[ -d "$ROOT_DIR" ] && pass "后端仓库目录存在: $ROOT_DIR" || fail "后端仓库目录不存在: $ROOT_DIR"
[ -d "$PLUS_UI_DIR" ] && pass "plus-ui 仓库目录存在: $PLUS_UI_DIR" || fail "plus-ui 仓库目录不存在: $PLUS_UI_DIR"
[ -d "$UNIAPP_DIR" ] && pass "reader-uniapp 仓库目录存在: $UNIAPP_DIR" || fail "reader-uniapp 仓库目录不存在: $UNIAPP_DIR"

print_header "2. 基础命令"

require_command java "Java 命令"
require_command pnpm "pnpm 命令"
if command -v curl >/dev/null 2>&1; then
  pass "curl 命令: $(command -v curl)"
else
  fail "curl 命令未找到"
fi

if command -v docker >/dev/null 2>&1; then
  pass "docker 命令: $(command -v docker)"
else
  warn "docker 命令未找到，如需用项目自带 compose 启动 MySQL/Redis，请先安装 Docker"
fi

print_header "3. JDK 21"

JAVA21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
if [ -n "$JAVA21_HOME" ]; then
  pass "检测到 JDK 21: $JAVA21_HOME"
  "$JAVA21_HOME/bin/java" -version 2>&1 | sed -n '1,2p'
else
  fail "未检测到 JDK 21"
fi

print_header "4. 本地依赖端口"

check_tcp "$MYSQL_HOST" "$MYSQL_PORT" "远程 MySQL"
check_tcp "$REDIS_HOST" "$REDIS_PORT" "远程 Redis"
check_port 8080 "RuoYi 后端"
check_port 5173 "plus-ui 开发服务"
check_port 5175 "reader-uniapp H5 开发服务"

print_header "5. HTTP 可达性"

check_http "http://${ACTUATOR_USER}:${ACTUATOR_PASSWORD}@localhost:8080/actuator/health" "后端健康检查"
check_http "http://localhost:5173/" "plus-ui 首页"
check_http "http://localhost:5175/" "reader-uniapp H5 首页"

print_header "6. reader-uniapp 联调环境变量"

if [ -n "$UNIAPP_ENV_FILE" ]; then
  pass "发现 reader-uniapp 环境文件: $UNIAPP_ENV_FILE"
else
  warn "未发现 reader-uniapp 环境文件，建议基于 .env.development.example 创建"
fi

READER_API_BASE_URL=$(read_env_value "VITE_READER_API_BASE_URL" || true)
READER_CLIENT_ID=$(read_env_value "VITE_READER_CLIENT_ID" || true)
READER_DEV_TOKEN=$(read_env_value "VITE_READER_DEV_TOKEN" || true)

if [ -n "$READER_API_BASE_URL" ]; then
  pass "VITE_READER_API_BASE_URL 已配置: $READER_API_BASE_URL"
else
  warn "VITE_READER_API_BASE_URL 未配置，将回退到默认 http://localhost:8080"
fi

if [ -n "$READER_CLIENT_ID" ]; then
  pass "VITE_READER_CLIENT_ID 已配置"
else
  warn "VITE_READER_CLIENT_ID 未配置，登录态接口可能报客户端ID与Token不匹配"
fi

if [ -n "$READER_DEV_TOKEN" ]; then
  pass "VITE_READER_DEV_TOKEN 已配置，可用于第一次本地联调验证登录态接口"
else
  warn "VITE_READER_DEV_TOKEN 未配置，如需验证书架/历史/进度接口，请先准备 Sa-Token"
fi

print_header "7. 项目内可用依赖方案"

if [ -f "$ROOT_DIR/script/docker/docker-compose.yml" ]; then
  pass "发现项目自带 Docker 编排: $ROOT_DIR/script/docker/docker-compose.yml"
else
  fail "未发现项目自带 Docker 编排文件"
fi

printf '\n== 检查结果汇总 ==\n'
printf 'PASS: %s\n' "$pass_count"
printf 'WARN: %s\n' "$warn_count"
printf 'FAIL: %s\n' "$fail_count"

if [ "$fail_count" -gt 0 ]; then
  printf '\n当前环境尚不满足第一次真实 P0 冒烟，请优先处理 FAIL 项。\n'
  exit 1
fi

printf '\n当前环境已基本具备第一次真实 P0 冒烟条件，可继续执行联调。\n'
