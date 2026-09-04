package org.dromara.reader.support;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 兼容项目父级 surefire 的环境分组配置，确保阅读器模块单元测试在各环境下都可被发现执行。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("dev")
@Tag("local")
@Tag("prod")
public @interface ReaderAllEnvTest {
}
