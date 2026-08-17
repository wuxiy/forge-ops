package com.forgeops.spring.boot.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * 自动装配行为验证：
 * 1. 引入依赖默认启用：Filter / 端点 / Store 全部装配
 * 2. forgeops.tracing.enabled=false：整体不装配
 * 3. forgeops.tracing.version-endpoint=false：端点属性生效
 */
class ForgeOpsTracingAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ForgeOpsTracingAutoConfiguration.class));

    @Test
    void autoConfiguresByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RequestIdFilter.class);
            assertThat(context).hasSingleBean(TracingEndpoints.class);
            assertThat(context).hasSingleBean(RequestLogStore.class);
            assertThat(context).hasSingleBean(AppVersionInfo.class);
        });
    }

    @Test
    void disabledByProperty() {
        runner.withPropertyValues("forgeops.tracing.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(RequestIdFilter.class)
                        .doesNotHaveBean(TracingEndpoints.class));
    }

    @Test
    void propertiesAreBound() {
        runner.withPropertyValues(
                "forgeops.tracing.version-endpoint=false",
                "forgeops.tracing.tracked-requests=128").run(context -> {
            ForgeOpsTracingProperties props = context.getBean(ForgeOpsTracingProperties.class);
            assertThat(props.isVersionEndpoint()).isFalse();
            assertThat(props.isExposeRequestLogs()).isTrue();
            assertThat(props.getTrackedRequests()).isEqualTo(128);
        });
    }
}
