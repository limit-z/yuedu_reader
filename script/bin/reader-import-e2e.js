#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const http = require('http');
const https = require('https');
const { URL } = require('url');

const MYSQL_JAR = '/Users/yabin/.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar';

const CONFIG = {
  baseUrl: process.env.READER_BASE_URL || 'http://127.0.0.1:8080',
  username: process.env.READER_ADMIN_USERNAME || 'admin',
  password: process.env.READER_ADMIN_PASSWORD,
  clientId: process.env.READER_CLIENT_ID || 'e5cd7e4891bf95d1d19206ce24a7b32e',
  sourceFile: process.env.READER_IMPORT_FILE || '/Users/yabin/data/book/谁还不是个修行者了.txt',
  mysql: {
    host: process.env.READER_DB_HOST,
    port: process.env.READER_DB_PORT || '3306',
    database: process.env.READER_DB_NAME || 'reader',
    username: process.env.READER_DB_USER,
    password: process.env.READER_DB_PASSWORD
  },
  redis: {
    host: process.env.READER_REDIS_HOST,
    port: process.env.READER_REDIS_PORT || '6379',
    database: Number(process.env.READER_REDIS_DB || '0'),
    password: process.env.READER_REDIS_PASSWORD
  },
  encrypt: {
    headerFlag: 'encrypt-key',
    requestPublicKey: process.env.READER_REQUEST_PUBLIC_KEY || 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDvEDuRIOM3oZPWj9Ukoc5pQklR4PFH6/clnjeFqjDLIgDyQvjxhgqAZQA+E9eD6qu6FsXPmK8djcL+nh3cFHz4pX473jDvO3Sve+8yL3VRQ0n2pRgQ2a01MJsy+WwTZCBYWf0VnLRIvANUoWQgy9vz94q7Va44dg7A1/3ICf+xAwIDAQAB'
  }
};

function log(step, message) {
  console.log(`[${step}] ${message}`);
}

function fail(message, extra) {
  console.error(`[FAIL] ${message}`);
  if (extra) {
    console.error(extra);
  }
  process.exit(1);
}

function ensureFile(filePath) {
  if (!fs.existsSync(filePath)) {
    fail(`文件不存在: ${filePath}`);
  }
}

function buildMultipart(fields, fileField, filePath) {
  const boundary = `----CodexBoundary${crypto.randomBytes(12).toString('hex')}`;
  const chunks = [];

  Object.entries(fields).forEach(([key, value]) => {
    chunks.push(Buffer.from(`--${boundary}\r\n`));
    chunks.push(Buffer.from(`Content-Disposition: form-data; name="${key}"\r\n\r\n`));
    chunks.push(Buffer.from(String(value)));
    chunks.push(Buffer.from('\r\n'));
  });

  const fileName = path.basename(filePath);
  const fileBuffer = fs.readFileSync(filePath);
  chunks.push(Buffer.from(`--${boundary}\r\n`));
  chunks.push(Buffer.from(`Content-Disposition: form-data; name="${fileField}"; filename="${fileName}"\r\n`));
  chunks.push(Buffer.from('Content-Type: application/octet-stream\r\n\r\n'));
  chunks.push(fileBuffer);
  chunks.push(Buffer.from('\r\n'));
  chunks.push(Buffer.from(`--${boundary}--\r\n`));

  return {
    boundary,
    body: Buffer.concat(chunks)
  };
}

function httpRequest(method, requestUrl, headers = {}, body = null) {
  const urlObj = new URL(requestUrl);
  const transport = urlObj.protocol === 'https:' ? https : http;
  const options = {
    method,
    hostname: urlObj.hostname,
    port: urlObj.port || (urlObj.protocol === 'https:' ? 443 : 80),
    path: `${urlObj.pathname}${urlObj.search}`,
    headers
  };

  return new Promise((resolve, reject) => {
    const req = transport.request(options, res => {
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => {
        const buffer = Buffer.concat(chunks);
        resolve({
          status: res.statusCode,
          headers: res.headers,
          body: buffer.toString('utf8')
        });
      });
    });
    req.on('error', reject);
    if (body) {
      req.write(body);
    }
    req.end();
  });
}

function encryptLoginPayload(payload) {
  const aesKey = crypto.randomBytes(16).toString('hex');
  const base64Key = Buffer.from(aesKey, 'utf8').toString('base64');
  const encryptedHeader = crypto.publicEncrypt(
    {
      key: `-----BEGIN PUBLIC KEY-----\n${CONFIG.encrypt.requestPublicKey}\n-----END PUBLIC KEY-----`,
      padding: crypto.constants.RSA_PKCS1_PADDING
    },
    Buffer.from(base64Key, 'utf8')
  ).toString('base64');

  const cipher = crypto.createCipheriv('aes-256-ecb', Buffer.from(aesKey, 'utf8'), null);
  cipher.setAutoPadding(true);
  const encryptedBody = Buffer.concat([
    cipher.update(Buffer.from(JSON.stringify(payload), 'utf8')),
    cipher.final()
  ]).toString('base64');

  return {
    encryptedHeader,
    encryptedBody
  };
}

async function getCaptcha() {
  const response = await httpRequest('GET', `${CONFIG.baseUrl}/auth/code`, {
    Accept: 'application/json'
  });
  if (response.status !== 200) {
    fail(`获取验证码失败: HTTP ${response.status}`, response.body);
  }
  const parsed = JSON.parse(response.body);
  if (!parsed?.data?.captchaEnabled) {
    return { uuid: null, code: null };
  }
  return {
    uuid: parsed.data.uuid
  };
}

async function runJava(className, javaSource) {
  const tempDir = fs.mkdtempSync(path.join(require('os').tmpdir(), 'reader-e2e-'));
  const javaFile = path.join(tempDir, `${className}.java`);
  fs.writeFileSync(javaFile, javaSource, 'utf8');

  const classpath = [MYSQL_JAR].join(':');
  await exec(`javac -cp "${classpath}" "${javaFile}"`);
  const output = await exec(`java -cp "${classpath}:${tempDir}" ${className}`);
  return output.trim();
}

function escapeJava(value) {
  return String(value)
    .replace(/\\/g, '\\\\')
    .replace(/"/g, '\\"');
}

async function getCaptchaCodeFromRedis(uuid) {
  const code = await redisGet(`global:captcha_codes:${uuid}`);
  if (!code) {
    fail(`Redis 中未读取到验证码, uuid=${uuid}`);
  }
  return code;
}

async function login() {
  const captcha = await getCaptcha();
  let code = null;
  if (captcha.uuid) {
    log('captcha', `获取到验证码 uuid=${captcha.uuid}，准备从 Redis 读取实际值`);
    code = await getCaptchaCodeFromRedis(captcha.uuid);
    log('captcha', `验证码读取成功: ${code}`);
  }

  const payload = {
    clientId: CONFIG.clientId,
    grantType: 'password',
    username: CONFIG.username,
    password: CONFIG.password
  };
  if (captcha.uuid) {
    payload.uuid = captcha.uuid;
    payload.code = code;
  }

  const encrypted = encryptLoginPayload(payload);
  const response = await httpRequest(
    'POST',
    `${CONFIG.baseUrl}/auth/login`,
    {
      Accept: 'application/json',
      'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Codex/1.0 Safari/537.36',
      'Content-Type': 'application/json;charset=utf-8',
      [CONFIG.encrypt.headerFlag]: encrypted.encryptedHeader,
      clientid: CONFIG.clientId
    },
    encrypted.encryptedBody
  );

  if (response.status !== 200) {
    fail(`登录失败: HTTP ${response.status}`, response.body);
  }
  const parsed = JSON.parse(response.body);
  const accessToken = parsed.data?.accessToken || parsed.data?.access_token;
  if (parsed.code !== 200 || !accessToken) {
    fail('登录返回异常', response.body);
  }
  return accessToken;
}

async function verifyToken(token) {
  const response = await httpRequest('GET', `${CONFIG.baseUrl}/system/user/getInfo`, {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    clientid: CONFIG.clientId
  });
  if (response.status !== 200) {
    fail(`登录态校验失败: HTTP ${response.status}`, response.body);
  }
  const parsed = JSON.parse(response.body);
  if (parsed.code !== 200 || !parsed.data?.user?.userName) {
    fail('登录态校验返回异常', response.body);
  }
  log('login', `当前登录用户: ${parsed.data.user.userName}`);
}

async function uploadToOss(token) {
  const multipart = buildMultipart({}, 'file', CONFIG.sourceFile);
  const response = await httpRequest(
    'POST',
    `${CONFIG.baseUrl}/resource/oss/upload`,
    {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
      clientid: CONFIG.clientId,
      'Content-Type': `multipart/form-data; boundary=${multipart.boundary}`,
      'Content-Length': String(multipart.body.length)
    },
    multipart.body
  );
  if (response.status !== 200) {
    fail(`OSS 上传失败: HTTP ${response.status}`, response.body);
  }
  const parsed = JSON.parse(response.body);
  const ossId = parsed.data?.ossId ? String(parsed.data.ossId) : extractJsonValue(response.body, 'ossId');
  if (parsed.code !== 200 || !ossId) {
    fail('OSS 上传返回异常', response.body);
  }
  return {
    ossId,
    url: parsed.data?.url
  };
}

async function createImportTask(token, ossId) {
  const taskName = `谁还不是个修行者了-回归-${Date.now()}`;
  const response = await httpRequest(
    'POST',
    `${CONFIG.baseUrl}/reader/admin/import/tasks`,
    {
      Accept: 'application/json',
      'Content-Type': 'application/json;charset=utf-8',
      Authorization: `Bearer ${token}`,
      clientid: CONFIG.clientId
    },
    JSON.stringify({
      taskName,
      contentType: 'NOVEL',
      ossId
    })
  );

  if (response.status !== 200) {
    fail(`创建导入任务失败: HTTP ${response.status}`, response.body);
  }
  const parsed = JSON.parse(response.body);
  const taskId = parsed.data ? String(parsed.data) : extractJsonValue(response.body, 'data');
  if (parsed.code !== 200 || !taskId) {
    fail('创建导入任务返回异常', response.body);
  }
  return {
    taskId,
    taskName
  };
}

async function approveAudit(token, auditId) {
  const response = await httpRequest(
    'POST',
    `${CONFIG.baseUrl}/reader/admin/audits/${auditId}/approve`,
    {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
      clientid: CONFIG.clientId
    }
  );
  if (response.status !== 200) {
    fail(`审核通过失败: HTTP ${response.status}`, response.body);
  }
  const parsed = JSON.parse(response.body);
  if (parsed.code !== 200) {
    fail('审核通过返回异常', response.body);
  }
}

async function getWorkIdAndChapterIdFromDb(taskName) {
  const javaSource = `
import java.sql.*;

public class QueryImportResult {
    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = "jdbc:mysql://${escapeJava(CONFIG.mysql.host)}:${escapeJava(CONFIG.mysql.port)}/${escapeJava(CONFIG.mysql.database)}?serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false&zeroDateTimeBehavior=convertToNull";
        try (Connection conn = DriverManager.getConnection(url, "${escapeJava(CONFIG.mysql.username)}", "${escapeJava(CONFIG.mysql.password)}")) {
            String taskStatus = null;
            String failReason = null;
            try (PreparedStatement taskStmt = conn.prepareStatement(
                "select status, fail_reason from reader_import_task where task_name = ? order by id desc limit 1")) {
                taskStmt.setString(1, "${escapeJava(taskName)}");
                try (ResultSet rs = taskStmt.executeQuery()) {
                    if (rs.next()) {
                        taskStatus = rs.getString("status");
                        failReason = rs.getString("fail_reason");
                    }
                }
            }
            if (taskStatus != null) {
                System.out.println("TASK|" + taskStatus + "|" + (failReason == null ? "" : failReason));
            }
            try (PreparedStatement workStmt = conn.prepareStatement(
                "select id, title, publish_status from reader_work where title = ? order by id desc limit 1")) {
                workStmt.setString(1, "${escapeJava(taskName)}");
                try (ResultSet rs = workStmt.executeQuery()) {
                    if (!rs.next()) {
                        return;
                    }
                    String workId = rs.getString("id");
                    String title = rs.getString("title");
                    String publishStatus = rs.getString("publish_status");
                    String auditId = null;
                    try (PreparedStatement auditStmt = conn.prepareStatement(
                        "select id from reader_content_audit where work_id = ? order by id desc limit 1")) {
                        auditStmt.setString(1, workId);
                        try (ResultSet auditRs = auditStmt.executeQuery()) {
                            if (auditRs.next()) {
                                auditId = auditRs.getString("id");
                            }
                        }
                    }
                    try (PreparedStatement chapterStmt = conn.prepareStatement(
                        "select id, left(content, 80) as preview from reader_novel_chapter where work_id = ? order by chapter_no asc, id asc limit 1")) {
                        chapterStmt.setString(1, workId);
                        try (ResultSet chapterRs = chapterStmt.executeQuery()) {
                            if (chapterRs.next()) {
                                System.out.println("WORK|" + workId + "|" + title + "|" + publishStatus + "|" + (auditId == null ? "" : auditId) + "|" + chapterRs.getString("id") + "|" + chapterRs.getString("preview"));
                            } else {
                                System.out.println("WORK|" + workId + "|" + title + "|" + publishStatus + "|" + (auditId == null ? "" : auditId) + "||");
                            }
                        }
                    }
                }
            }
        }
    }
}
`;
  const output = await runJava('QueryImportResult', javaSource);
  return output;
}

async function getPublishedChapterContent(chapterId) {
  const response = await httpRequest(
    'GET',
    `${CONFIG.baseUrl}/reader/app/reading/novels/${chapterId}`,
    {
      Accept: 'application/json',
      Authorization: `Bearer ${CONFIG.accessToken}`,
      clientid: CONFIG.clientId
    }
  );
  if (response.status !== 200) {
    fail(`阅读接口校验失败: HTTP ${response.status}`, response.body);
  }
  const parsed = JSON.parse(response.body);
  if (parsed.code !== 200 || !parsed.data?.content) {
    fail('阅读接口返回异常', response.body);
  }
  return parsed.data;
}

function exec(command) {
  return new Promise((resolve, reject) => {
    const child = require('child_process').exec(command, { maxBuffer: 10 * 1024 * 1024 }, (error, stdout, stderr) => {
      if (error) {
        reject(new Error(`${command}\n${stderr || stdout || error.message}`));
        return;
      }
      resolve(stdout);
    });
    child.stdin && child.stdin.end();
  });
}

async function redisGet(key) {
  const net = require('net');
  const commands = [
    `*2\r\n$4\r\nAUTH\r\n$${Buffer.byteLength(CONFIG.redis.password)}\r\n${CONFIG.redis.password}\r\n`,
    `*2\r\n$6\r\nSELECT\r\n$${Buffer.byteLength(String(CONFIG.redis.database))}\r\n${CONFIG.redis.database}\r\n`,
    `*2\r\n$3\r\nGET\r\n$${Buffer.byteLength(key)}\r\n${key}\r\n`
  ];

  function parseResp(buffer) {
    if (!buffer.length) {
      return null;
    }
    const type = buffer[0];
    const lineEnd = buffer.indexOf('\r\n');
    if (lineEnd === -1) {
      return null;
    }
    const line = buffer.slice(1, lineEnd);
    const rest = buffer.slice(lineEnd + 2);
    if (type === '+') {
      return { value: line, rest };
    }
    if (type === ':') {
      return { value: Number(line), rest };
    }
    if (type === '$') {
      const len = Number(line);
      if (len === -1) {
        return { value: null, rest };
      }
      if (rest.length < len + 2) {
        return null;
      }
      return { value: rest.slice(0, len), rest: rest.slice(len + 2) };
    }
    if (type === '-') {
      return { value: new Error(line), rest };
    }
    return { value: null, rest };
  }

  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: CONFIG.redis.host, port: Number(CONFIG.redis.port) }, () => {
      for (const cmd of commands) {
        socket.write(cmd);
      }
    });
    let buffer = '';
    let replyCount = 0;
    socket.setEncoding('utf8');
    socket.on('data', chunk => {
      buffer += chunk;
      while (true) {
        const parsed = parseResp(buffer);
        if (!parsed) {
          break;
        }
        buffer = parsed.rest;
        replyCount += 1;
        if (parsed.value instanceof Error) {
          reject(parsed.value);
          socket.destroy();
          return;
        }
        if (replyCount === 3) {
          resolve(parsed.value);
          socket.end();
          return;
        }
      }
    });
    socket.on('error', reject);
    socket.on('close', () => {
      if (replyCount < 3) {
        reject(new Error('Redis 连接提前关闭，未拿到验证码'));
      }
    });
  });
}

async function main() {
  ensureFile(CONFIG.sourceFile);

  log('start', `准备验证文件导入链路: ${CONFIG.sourceFile}`);
  const token = await login();
  CONFIG.accessToken = token;
  log('login', '登录成功，拿到 access token');
  await verifyToken(token);

  const oss = await uploadToOss(token);
  log('oss', `上传成功，ossId=${oss.ossId}`);

  const task = await createImportTask(token, oss.ossId);
  log('task', `导入任务已创建，taskId=${task.taskId}, taskName=${task.taskName}`);

  const beforeApprove = await getWorkIdAndChapterIdFromDb(task.taskName);
  if (!beforeApprove) {
    fail('数据库未查到导入结果');
  }
  const beforeLines = beforeApprove.split('\n');
  const taskLine = beforeLines.find(line => line.startsWith('TASK|'));
  const workLine = beforeLines.find(line => line.startsWith('WORK|'));
  if (!taskLine) {
    fail('未查到导入任务状态');
  }
  log('task', `任务状态=${taskLine.split('|')[1]}${taskLine.split('|')[2] ? `, failReason=${taskLine.split('|')[2]}` : ''}`);
  if (taskLine.split('|')[1] === 'PARSE_FAILED') {
    fail('导入任务仍然失败', taskLine);
  }
  if (!workLine) {
    fail('未查到导入作品与章节信息');
  }
  const workParts = workLine.split('|');
  const workId = workParts[1];
  const title = workParts[2];
  const publishStatus = workParts[3];
  const auditId = workParts[4];
  const chapterId = workParts[5];
  const preview = workParts.slice(6).join('|');
  log('work', `作品已生成, workId=${workId}, publishStatus=${publishStatus}, title=${title}`);

  if (!auditId) {
    fail('未查到审核记录');
  }
  await approveAudit(token, auditId);
  log('audit', `审核通过成功, auditId=${auditId}`);

  const dbResult = await getWorkIdAndChapterIdFromDb(task.taskName);
  if (!dbResult) {
    fail('数据库未查询到导入结果');
  }
  log('db', `数据库结果: ${dbResult}`);

  const refreshedWorkLine = dbResult.split('\n').find(line => line.startsWith('WORK|'));
  if (!refreshedWorkLine) {
    fail('审核后未查到作品记录');
  }
  const refreshedParts = refreshedWorkLine.split('|');
  const publishedChapterId = refreshedParts[5];
  if (!publishedChapterId) {
    fail('审核后未查到章节ID');
  }
  const reading = await getPublishedChapterContent(publishedChapterId);
  log('read', `阅读接口校验成功，chapterId=${publishedChapterId}, contentPreview=${reading.content.slice(0, 60).replace(/\\s+/g, ' ')}`);

  console.log('\n=== RESULT ===');
  console.log(JSON.stringify({
    success: true,
    taskId: task.taskId,
    taskName: task.taskName,
    ossId: oss.ossId,
    workId,
    chapterId: publishedChapterId,
    publishStatus: refreshedParts[3],
    contentPreview: reading.content.slice(0, 120)
  }, null, 2));
}

function extractJsonNumber(body, key) {
  const match = body.match(new RegExp(`"${key}"\\s*:\\s*(\\d+)`));
  return match ? match[1] : null;
}

function extractJsonValue(body, key) {
  const stringMatch = body.match(new RegExp(`"${key}"\\s*:\\s*"([^"]+)"`));
  if (stringMatch) {
    return stringMatch[1];
  }
  return extractJsonNumber(body, key);
}

main().catch(error => {
  fail('执行过程中出现异常', error.stack || error.message);
});
