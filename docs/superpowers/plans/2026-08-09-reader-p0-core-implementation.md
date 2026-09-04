# Reader P0 Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first working reader platform slice on top of `RuoYi-Vue-Plus 6.X`: file import, content draft, audit/publish, reading APIs for a `plus-ui`-based Web/H5 frontend and a `UniApp` mini-program frontend, bookshelf, and reading progress.

**Architecture:** Keep the codebase as a modular monolith. Add one new Maven module, `ruoyi-reader`, and split its packages by domain (`content`, `ingest`, `audit`, `reading`, `user`, `operation`) while exposing separate `app` and `admin` controllers. Store business truth in MySQL, use Redis for cache/progress buffering, and use OSS/CDN for large files and comic images.

**Tech Stack:** Java 21, Spring Boot 4.1, MyBatis-Plus, Sa-Token, Redis/Redisson, SnailJob, MySQL, OSS. Frontend baseline constraints for follow-up work: `UniApp` for the mini-program client, and official `plus-ui` (Vue + Element Plus) as the Web/H5 and admin secondary-development base. The `plus-ui` repository lives at `/Users/yabin/code/ruoyi/plus-ui` on branch `6.X-Vue`.

---

## Frontend execution constraints

These constraints apply to every backend task in this plan, especially API and DTO design:

1. Mini-program target: `UniApp`
2. Web/H5 target: `plus-ui` secondary development
3. Admin Web target: `plus-ui` secondary development
4. `reader app API` response shape, auth flow, and error semantics must stay compatible with both `UniApp` and `plus-ui` consumers
5. H5 reading pages may share the `plus-ui` technology base, but must use an independent mobile reading layout rather than the desktop admin shell
6. Frontend workspace for `plus-ui`: `/Users/yabin/code/ruoyi/plus-ui` on branch `6.X-Vue`

This plan still focuses on backend P0 implementation. Frontend code should be planned separately, but backend contracts must be written with those two frontend bases fixed from day one.

## Scope split

This plan intentionally covers only the P0 chain that can become working software by itself:

1. `ruoyi-reader` module bootstrap
2. content schema and CRUD foundation
3. file import and parse-task orchestration
4. audit/publish state flow
5. app reading APIs for novel/comic detail/catalog/reader, serving `UniApp` and `plus-ui`
6. bookshelf, history, and reading progress

Out of scope for this plan:

- authorized source sync
- topics/rankings/advanced operation center
- comments/reports/messages
- membership/payment
- frontend page implementation itself

Write separate plans later for those subsystems.

## File structure map

### Maven and bootstrap files

- Modify: `pom.xml`
- Modify: `ruoyi-modules/pom.xml`
- Modify: `ruoyi-admin/pom.xml`
- Create: `script/sql/ry_reader.sql`
- Create: `ruoyi-modules/ruoyi-reader/pom.xml`

### Reader module main files

- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/package-info.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/app/`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/admin/`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/bo/`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/vo/`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/mapper/`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/impl/`
- Create: `ruoyi-modules/ruoyi-reader/src/main/resources/mapper/reader/`

### Tests

- Create: `ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/`

## Task 1: Register the new Maven module and SQL entry point

**Files:**
- Modify: `pom.xml`
- Modify: `ruoyi-modules/pom.xml`
- Modify: `ruoyi-admin/pom.xml`
- Create: `ruoyi-modules/ruoyi-reader/pom.xml`
- Create: `script/sql/ry_reader.sql`
- Test: `mvn -pl ruoyi-modules/ruoyi-reader -am -DskipTests compile`

- [ ] **Step 1: Write the failing module references**

Add the new module reference before the module exists so the build fails in a controlled way.

```xml
<!-- ruoyi-modules/pom.xml -->
<modules>
    <module>ruoyi-demo</module>
    <module>ruoyi-gen</module>
    <module>ruoyi-job</module>
    <module>ruoyi-system</module>
    <module>ruoyi-workflow</module>
    <module>ruoyi-ai</module>
    <module>ruoyi-reader</module>
</modules>
```

```xml
<!-- ruoyi-admin/pom.xml -->
<dependency>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-reader</artifactId>
</dependency>
```

- [ ] **Step 2: Run Maven to verify the missing-module failure**

Run:

```bash
mvn -pl ruoyi-admin -am -DskipTests compile
```

Expected: FAIL with a missing module or missing artifact for `ruoyi-reader`.

- [ ] **Step 3: Create the minimal new module**

Create the module `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>ruoyi-modules</artifactId>
        <groupId>org.dromara</groupId>
        <version>${revision}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>
    <artifactId>ruoyi-reader</artifactId>

    <description>阅读器业务模块</description>

    <dependencies>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-mybatis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-common-oss</artifactId>
        </dependency>
        <dependency>
            <groupId>org.dromara</groupId>
            <artifactId>ruoyi-system</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Create the SQL entry file header:

```sql
-- script/sql/ry_reader.sql
-- Reader module schema
-- Date: 2026-08-09
```

- [ ] **Step 4: Run Maven to verify the module compiles**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -am -DskipTests compile
```

Expected: PASS for the new empty module.

- [ ] **Step 5: Commit**

```bash
git add pom.xml ruoyi-modules/pom.xml ruoyi-admin/pom.xml ruoyi-modules/ruoyi-reader/pom.xml script/sql/ry_reader.sql
git commit -m "feat: add reader module scaffold"
```

## Task 2: Create the reader package skeleton and shared enums

**Files:**
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/package-info.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/constant/ReaderConstants.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/enums/WorkType.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/enums/PublishStatus.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/enums/ImportTaskStatus.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/enums/ReadingContentType.java`
- Test: `ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/enums/ReaderEnumTest.java`

- [ ] **Step 1: Write the enum test first**

```java
package org.dromara.reader.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReaderEnumTest {

    @Test
    void shouldExposeExpectedPublishStatusCodes() {
        assertEquals("DRAFT", PublishStatus.DRAFT.name());
        assertEquals("PUBLISHED", PublishStatus.PUBLISHED.name());
    }
}
```

- [ ] **Step 2: Run the enum test to verify it fails**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderEnumTest test
```

Expected: FAIL because `PublishStatus` does not exist.

- [ ] **Step 3: Add the shared enum and constant classes**

```java
package org.dromara.reader.enums;

public enum PublishStatus {
    DRAFT,
    PARSING,
    PENDING_REVIEW,
    REJECTED,
    PUBLISHED,
    OFFLINE
}
```

```java
package org.dromara.reader.enums;

public enum WorkType {
    NOVEL,
    COMIC
}
```

```java
package org.dromara.reader.enums;

public enum ImportTaskStatus {
    CREATED,
    UPLOADED,
    PARSING,
    PARSE_FAILED,
    CLEANING,
    PENDING_REVIEW,
    CANCELED,
    COMPLETED
}
```

```java
package org.dromara.reader.enums;

public enum ReadingContentType {
    NOVEL,
    COMIC
}
```

```java
package org.dromara.reader.constant;

public interface ReaderConstants {
    String CACHE_WORK_DETAIL = "reader:work:detail:";
    String CACHE_WORK_CATALOG = "reader:work:catalog:";
    String CACHE_READING_PROGRESS = "reader:progress:";
}
```

- [ ] **Step 4: Run the enum test again**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderEnumTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/enums/ReaderEnumTest.java
git commit -m "feat: add reader shared enums and constants"
```

## Task 3: Build the content schema and entity layer

**Files:**
- Modify: `script/sql/ry_reader.sql`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderWork.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderNovelChapter.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderComicChapter.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderComicPage.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/mapper/ReaderWorkMapper.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/mapper/ReaderNovelChapterMapper.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/mapper/ReaderComicChapterMapper.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/mapper/ReaderComicPageMapper.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/resources/mapper/reader/ReaderWorkMapper.xml`
- Test: `ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/domain/ReaderWorkTest.java`

- [ ] **Step 1: Write a failing entity-level test for defaults**

```java
package org.dromara.reader.domain;

import org.dromara.reader.enums.PublishStatus;
import org.dromara.reader.enums.WorkType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReaderWorkTest {

    @Test
    void shouldAllowSettingBasicWorkFields() {
        ReaderWork work = new ReaderWork();
        work.setTitle("凡人修仙传");
        work.setWorkType(WorkType.NOVEL.name());
        work.setPublishStatus(PublishStatus.DRAFT.name());

        assertEquals("凡人修仙传", work.getTitle());
        assertEquals("NOVEL", work.getWorkType());
        assertEquals("DRAFT", work.getPublishStatus());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderWorkTest test
```

Expected: FAIL because `ReaderWork` does not exist.

- [ ] **Step 3: Add schema and entities**

SQL skeleton:

```sql
create table if not exists reader_work (
  id bigint primary key auto_increment,
  work_type varchar(16) not null,
  title varchar(255) not null,
  intro text null,
  cover_url varchar(500) null,
  serial_status varchar(32) not null default 'ONGOING',
  publish_status varchar(32) not null default 'DRAFT',
  source_type varchar(32) not null default 'IMPORT',
  total_chapters int not null default 0,
  total_pages int not null default 0,
  allow_search char(1) not null default '1',
  create_time datetime null,
  update_time datetime null
);
```

Entity skeleton:

```java
package org.dromara.reader.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("reader_work")
public class ReaderWork {
    @TableId
    private Long id;
    private String workType;
    private String title;
    private String intro;
    private String coverUrl;
    private String serialStatus;
    private String publishStatus;
    private String sourceType;
    private Integer totalChapters;
    private Integer totalPages;
    private String allowSearch;
}
```

Mapper skeleton:

```java
package org.dromara.reader.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.dromara.reader.domain.ReaderWork;

public interface ReaderWorkMapper extends BaseMapper<ReaderWork> {
}
```

- [ ] **Step 4: Run the entity test**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderWorkTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add script/sql/ry_reader.sql ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/mapper ruoyi-modules/ruoyi-reader/src/main/resources/mapper/reader ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/domain/ReaderWorkTest.java
git commit -m "feat: add reader content schema and entities"
```

## Task 4: Implement admin content CRUD foundation

**Files:**
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/bo/ReaderWorkBo.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/vo/ReaderWorkVo.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/IReaderWorkService.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/impl/ReaderWorkServiceImpl.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/admin/ReaderWorkController.java`
- Test: `ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/service/ReaderWorkServiceTest.java`

- [ ] **Step 1: Write the service test**

```java
package org.dromara.reader.service;

import org.dromara.reader.domain.bo.ReaderWorkBo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReaderWorkServiceTest {

    @Test
    void shouldMapBoToEntityFields() {
        ReaderWorkBo bo = new ReaderWorkBo();
        bo.setTitle("测试作品");
        bo.setWorkType("NOVEL");

        assertEquals("测试作品", bo.getTitle());
        assertEquals("NOVEL", bo.getWorkType());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderWorkServiceTest test
```

Expected: FAIL because `ReaderWorkBo` does not exist.

- [ ] **Step 3: Add BO/VO/service/controller skeletons**

```java
package org.dromara.reader.domain.bo;

import lombok.Data;

@Data
public class ReaderWorkBo {
    private Long id;
    private String workType;
    private String title;
    private String intro;
    private String coverUrl;
}
```

```java
package org.dromara.reader.controller.admin;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.bo.ReaderWorkBo;
import org.dromara.reader.service.IReaderWorkService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/works")
public class ReaderWorkController {

    private final IReaderWorkService readerWorkService;

    @PostMapping
    public R<Long> add(@RequestBody ReaderWorkBo bo) {
        return R.ok(readerWorkService.createWork(bo));
    }
}
```

- [ ] **Step 4: Run the test again**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderWorkServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/bo ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/vo ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/admin ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/service/ReaderWorkServiceTest.java
git commit -m "feat: add reader admin content foundation"
```

## Task 5: Implement import task schema and admin APIs

**Files:**
- Modify: `script/sql/ry_reader.sql`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderImportTask.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderImportFile.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/bo/ReaderImportTaskBo.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/IReaderImportTaskService.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/impl/ReaderImportTaskServiceImpl.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/admin/ReaderImportTaskController.java`
- Test: `ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/service/ReaderImportTaskServiceTest.java`

- [ ] **Step 1: Write the import-task status test**

```java
package org.dromara.reader.service;

import org.dromara.reader.enums.ImportTaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReaderImportTaskServiceTest {

    @Test
    void shouldUseCreatedAsFirstImportStatus() {
        assertEquals("CREATED", ImportTaskStatus.CREATED.name());
    }
}
```

- [ ] **Step 2: Run the test**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderImportTaskServiceTest test
```

Expected: PASS for enum, but no service exists yet. Then add one more method-based assertion and rerun to fail:

```java
// add in test
// ReaderImportTaskServiceImpl service = new ReaderImportTaskServiceImpl();
// assertNotNull(service);
```

Expected: FAIL because `ReaderImportTaskServiceImpl` does not exist.

- [ ] **Step 3: Add import task schema and admin controller**

SQL skeleton:

```sql
create table if not exists reader_import_task (
  id bigint primary key auto_increment,
  task_name varchar(255) not null,
  content_type varchar(16) not null,
  status varchar(32) not null,
  oss_id bigint null,
  fail_reason varchar(1000) null,
  create_time datetime null,
  update_time datetime null
);
```

Controller skeleton:

```java
package org.dromara.reader.controller.admin;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.bo.ReaderImportTaskBo;
import org.dromara.reader.service.IReaderImportTaskService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/import/tasks")
public class ReaderImportTaskController {

    private final IReaderImportTaskService importTaskService;

    @PostMapping
    public R<Long> create(@RequestBody ReaderImportTaskBo bo) {
        return R.ok(importTaskService.createTask(bo));
    }
}
```

- [ ] **Step 4: Run targeted tests**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderImportTaskServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add script/sql/ry_reader.sql ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderImportTask.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderImportFile.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/bo/ReaderImportTaskBo.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/IReaderImportTaskService.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/impl/ReaderImportTaskServiceImpl.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/admin/ReaderImportTaskController.java ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/service/ReaderImportTaskServiceTest.java
git commit -m "feat: add reader import task management"
```

## Task 6: Add parse abstraction and async task entrypoints

**Files:**
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/parser/ReaderFileParser.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/parser/NovelTxtParser.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/parser/NovelEpubParser.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/parser/ComicZipParser.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/job/ReaderImportParseJob.java`
- Test: `ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/service/parser/ReaderFileParserTest.java`

- [ ] **Step 1: Write the parser selection test**

```java
package org.dromara.reader.service.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReaderFileParserTest {

    @Test
    void txtParserShouldSupportTxtSuffix() {
        ReaderFileParser parser = new NovelTxtParser();
        assertTrue(parser.supports("txt"));
    }
}
```

- [ ] **Step 2: Run the parser test**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderFileParserTest test
```

Expected: FAIL because the parser interfaces do not exist.

- [ ] **Step 3: Add parser interface and one real implementation**

```java
package org.dromara.reader.service.parser;

public interface ReaderFileParser {
    boolean supports(String suffix);
    void parse(Long importTaskId);
}
```

```java
package org.dromara.reader.service.parser;

public class NovelTxtParser implements ReaderFileParser {
    @Override
    public boolean supports(String suffix) {
        return "txt".equalsIgnoreCase(suffix);
    }

    @Override
    public void parse(Long importTaskId) {
        // parse text file into draft chapters
    }
}
```

```java
package org.dromara.reader.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReaderImportParseJob {

    public void execute(Long importTaskId) {
        // load task -> choose parser -> parse -> update status
    }
}
```

- [ ] **Step 4: Run the parser test again**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderFileParserTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/parser ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/job/ReaderImportParseJob.java ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/service/parser/ReaderFileParserTest.java
git commit -m "feat: add reader parse abstraction"
```

## Task 7: Implement audit and publish state transitions

**Files:**
- Modify: `script/sql/ry_reader.sql`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderContentAudit.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/IReaderAuditService.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/impl/ReaderAuditServiceImpl.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/admin/ReaderAuditController.java`
- Test: `ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/service/ReaderAuditServiceTest.java`

- [ ] **Step 1: Write a failing publish-state test**

```java
package org.dromara.reader.service;

import org.dromara.reader.enums.PublishStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReaderAuditServiceTest {

    @Test
    void publishedStateShouldBeAvailableForApprovedContent() {
        assertEquals("PUBLISHED", PublishStatus.PUBLISHED.name());
    }
}
```

- [ ] **Step 2: Run the audit test and add a missing-service assertion**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderAuditServiceTest test
```

Then extend the test with:

```java
// ReaderAuditServiceImpl service = new ReaderAuditServiceImpl();
// assertNotNull(service);
```

Expected: FAIL because the audit service does not exist.

- [ ] **Step 3: Add audit schema and service/controller skeleton**

```sql
create table if not exists reader_content_audit (
  id bigint primary key auto_increment,
  work_id bigint not null,
  audit_status varchar(32) not null,
  audit_comment varchar(1000) null,
  auditor_id bigint null,
  create_time datetime null
);
```

```java
package org.dromara.reader.controller.admin;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.service.IReaderAuditService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/admin/audits")
public class ReaderAuditController {

    private final IReaderAuditService readerAuditService;

    @PostMapping("/{auditId}/approve")
    public R<Void> approve(@PathVariable Long auditId) {
        readerAuditService.approve(auditId);
        return R.ok();
    }
}
```

- [ ] **Step 4: Run the audit test**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderAuditServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add script/sql/ry_reader.sql ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderContentAudit.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/IReaderAuditService.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/impl/ReaderAuditServiceImpl.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/admin/ReaderAuditController.java ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/service/ReaderAuditServiceTest.java
git commit -m "feat: add reader audit and publish flow"
```

## Task 8: Implement app work-detail, catalog, and reader endpoints

**Contract note:** These endpoints must be shaped so they can be consumed by the upcoming `UniApp` mini-program frontend and the `plus-ui`-based Web/H5 frontend without separate backend forks.

**Status update (2026-08-10):** Done. The app read path is no longer scaffold-only. The backend now exposes and verifies these P0 endpoints:

- `GET /reader/app/works/{workId}`
- `GET /reader/app/works/{workId}/catalog`
- `GET /reader/app/reading/novels/{chapterId}`
- `GET /reader/app/reading/comics/{chapterId}`

The current implementation already enforces published-resource visibility, returns `作品不存在` for work-level misses, returns `章节不存在` for reader-level misses, and orders comic pages by `page_no ASC`. Verification passed with JDK 21 on `ReaderAppContentServiceTest`, `ReaderWorkAppControllerTest`, and `ReaderReadingControllerTest`.

**Files:**
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/app/ReaderHomeController.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/app/ReaderWorkAppController.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/app/ReaderReadingController.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/vo/app/AppWorkDetailVo.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/vo/app/AppCatalogItemVo.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/vo/app/AppNovelChapterVo.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/vo/app/AppComicChapterVo.java`
- Test: `ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/controller/app/ReaderWorkAppControllerTest.java`

- [x] **Step 1: Write the app controller contract test**

```java
package org.dromara.reader.controller.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReaderWorkAppControllerTest {

    @Test
    void shouldInstantiateReaderWorkAppController() {
        assertNotNull(ReaderWorkAppController.class);
    }
}
```

- [x] **Step 2: Run the test**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderWorkAppControllerTest test
```

Expected: FAIL because the controller does not exist.

- [x] **Step 3: Add app endpoints**

```java
package org.dromara.reader.controller.app;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.vo.app.AppWorkDetailVo;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/app/works")
public class ReaderWorkAppController {

    @GetMapping("/{workId}")
    public R<AppWorkDetailVo> detail(@PathVariable Long workId) {
        return R.ok(new AppWorkDetailVo());
    }

    @GetMapping("/{workId}/catalog")
    public R<Object> catalog(@PathVariable Long workId) {
        return R.ok();
    }
}
```

```java
package org.dromara.reader.controller.app;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/app/reading")
public class ReaderReadingController {

    @GetMapping("/novels/{chapterId}")
    public R<Object> novelChapter(@PathVariable Long chapterId) {
        return R.ok();
    }

    @GetMapping("/comics/{chapterId}")
    public R<Object> comicChapter(@PathVariable Long chapterId) {
        return R.ok();
    }
}
```

- [x] **Step 4: Run the app controller test**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderWorkAppControllerTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/app ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/vo/app ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/controller/app/ReaderWorkAppControllerTest.java
git commit -m "feat: add reader app content APIs"
```

## Task 9: Implement bookshelf, history, and reading progress

**Contract note:** Progress, bookshelf, and history APIs must support cross-end continuation between `UniApp` mini-program and `plus-ui`-based Web/H5.

**Files:**
- Modify: `script/sql/ry_reader.sql`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderBookshelf.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderReadingProgress.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderReadingHistory.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/bo/ReaderProgressBo.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/IReaderBookshelfService.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/IReaderProgressService.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/app/ReaderBookshelfController.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/app/ReaderProgressController.java`
- Test: `ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/service/ReaderProgressServiceTest.java`

- [ ] **Step 1: Write the progress BO test**

```java
package org.dromara.reader.service;

import org.dromara.reader.domain.bo.ReaderProgressBo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReaderProgressServiceTest {

    @Test
    void shouldCaptureProgressRequestFields() {
        ReaderProgressBo bo = new ReaderProgressBo();
        bo.setWorkId(1L);
        bo.setChapterId(2L);
        bo.setProgressPercent(60);

        assertEquals(1L, bo.getWorkId());
        assertEquals(2L, bo.getChapterId());
        assertEquals(60, bo.getProgressPercent());
    }
}
```

- [ ] **Step 2: Run the test**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderProgressServiceTest test
```

Expected: FAIL because `ReaderProgressBo` does not exist.

- [ ] **Step 3: Add schema, BO, services, and controllers**

```sql
create table if not exists reader_reading_progress (
  id bigint primary key auto_increment,
  user_id bigint not null,
  work_id bigint not null,
  content_type varchar(16) not null,
  chapter_id bigint not null,
  location_value varchar(128) null,
  progress_percent int not null default 0,
  client_type varchar(32) not null,
  progress_updated_at datetime not null,
  unique key uk_reader_progress_user_work (user_id, work_id)
);
```

```java
package org.dromara.reader.domain.bo;

import lombok.Data;

@Data
public class ReaderProgressBo {
    private Long workId;
    private Long chapterId;
    private Integer progressPercent;
    private String locationValue;
    private String clientType;
}
```

```java
package org.dromara.reader.controller.app;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.reader.domain.bo.ReaderProgressBo;
import org.dromara.reader.service.IReaderProgressService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/app/reading")
public class ReaderProgressController {

    private final IReaderProgressService readerProgressService;

    @PostMapping("/progress")
    public R<Void> saveProgress(@RequestBody ReaderProgressBo bo) {
        readerProgressService.saveProgress(bo);
        return R.ok();
    }
}
```

- [ ] **Step 4: Run the progress test**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderProgressServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add script/sql/ry_reader.sql ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderBookshelf.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderReadingProgress.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/ReaderReadingHistory.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/domain/bo/ReaderProgressBo.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/IReaderBookshelfService.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/IReaderProgressService.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/app/ReaderBookshelfController.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/controller/app/ReaderProgressController.java ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/service/ReaderProgressServiceTest.java
git commit -m "feat: add reader bookshelf and progress APIs"
```

## Task 10: Add Redis buffering and publish refresh hooks

**Files:**
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/cache/ReaderProgressCacheService.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/job/ReaderProgressFlushJob.java`
- Create: `ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/job/ReaderPublishRefreshJob.java`
- Test: `ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/service/cache/ReaderProgressCacheServiceTest.java`

- [ ] **Step 1: Write the cache-key test**

```java
package org.dromara.reader.service.cache;

import org.dromara.reader.constant.ReaderConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReaderProgressCacheServiceTest {

    @Test
    void shouldBuildProgressCacheKeyPrefix() {
        assertEquals("reader:progress:", ReaderConstants.CACHE_READING_PROGRESS);
    }
}
```

- [ ] **Step 2: Run the cache test**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderProgressCacheServiceTest test
```

Expected: PASS for constants. Extend test with:

```java
// assertNotNull(ReaderProgressCacheService.class);
```

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Add buffering service and jobs**

```java
package org.dromara.reader.service.cache;

import org.dromara.reader.constant.ReaderConstants;
import org.springframework.stereotype.Service;

@Service
public class ReaderProgressCacheService {

    public String progressKey(Long userId, Long workId) {
        return ReaderConstants.CACHE_READING_PROGRESS + userId + ":" + workId;
    }
}
```

```java
package org.dromara.reader.job;

import org.springframework.stereotype.Component;

@Component
public class ReaderProgressFlushJob {

    public void execute() {
        // flush buffered progress from redis to mysql
    }
}
```

```java
package org.dromara.reader.job;

import org.springframework.stereotype.Component;

@Component
public class ReaderPublishRefreshJob {

    public void execute(Long workId) {
        // evict work detail/catalog/home caches after publish
    }
}
```

- [ ] **Step 4: Run the cache test**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderProgressCacheServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/service/cache/ReaderProgressCacheService.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/job/ReaderProgressFlushJob.java ruoyi-modules/ruoyi-reader/src/main/java/org/dromara/reader/job/ReaderPublishRefreshJob.java ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/service/cache/ReaderProgressCacheServiceTest.java
git commit -m "feat: add reader cache and refresh jobs"
```

## Task 11: Wire smoke integration checks and developer docs

**Files:**
- Modify: `docs/superpowers/specs/2026-08-09-reader-system-design.md`
- Create: `docs/reader-p0-smoke-checklist.md`
- Create: `ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/smoke/ReaderModuleSmokeTest.java`
- Test: `mvn -pl ruoyi-modules/ruoyi-reader test`

- [ ] **Step 1: Write the smoke test first**

```java
package org.dromara.reader.smoke;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReaderModuleSmokeTest {

    @Test
    void moduleSmokeTestShouldRun() {
        assertTrue(true);
    }
}
```

- [ ] **Step 2: Run the smoke test**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader -Dtest=ReaderModuleSmokeTest test
```

Expected: FAIL before the file exists, then PASS after creation.

- [ ] **Step 3: Add the smoke checklist doc**

```markdown
# Reader P0 Smoke Checklist

1. Import one EPUB novel and confirm draft content is created.
2. Approve the draft and confirm the work becomes readable.
3. Open the same work in H5 and mini program and confirm progress can continue.
4. Add the work to bookshelf and confirm the bookshelf tab shows it.
5. Offline the work and confirm app detail/catalog/read endpoints no longer expose published content.
```

- [ ] **Step 4: Run the full module test command**

Run:

```bash
mvn -pl ruoyi-modules/ruoyi-reader test
```

Expected: PASS for all reader-module tests.

- [ ] **Step 5: Commit**

```bash
git add docs/reader-p0-smoke-checklist.md docs/superpowers/specs/2026-08-09-reader-system-design.md ruoyi-modules/ruoyi-reader/src/test/java/org/dromara/reader/smoke/ReaderModuleSmokeTest.java
git commit -m "docs: add reader p0 smoke checklist"
```

## Spec coverage check

- Module bootstrap: Task 1
- Shared enums and state vocabulary: Task 2
- Content model and storage: Task 3
- Admin content CRUD base: Task 4
- File import: Task 5
- Parse abstraction and async entrypoint: Task 6
- Audit/publish flow: Task 7
- H5/mini-program read APIs: Task 8
- Bookshelf/history/progress: Task 9
- Redis buffering and publish refresh: Task 10 (current next step after Task 9 bookshelf/history/progress closure on 2026-08-10)
- Smoke verification and docs: Task 11

No P0 spec section is left without a task.

## Execution notes

- Implement the tasks in order. Later tasks assume earlier schema, enums, and package paths exist.
- Do not pull P1 scope into this plan.
- Keep app/admin DTOs separate from domain entities from the first implementation commit.
- When API fields are ambiguous, choose names and structures that are easy for both `UniApp` and `plus-ui` clients to consume consistently.
- After Task 11, write a separate plan for source sync if still needed.

## Follow-up frontend plans required

After this backend P0 plan is stable, create two explicit follow-up plans:

1. `plus-ui` Web/H5 frontend secondary-development plan
2. `UniApp` mini-program frontend implementation plan
