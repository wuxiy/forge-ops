package com.forgeops.spring.boot.tracing;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * ForgeOps Tracing 自动装配：引入依赖即生效（forgeops.tracing.enabled 默认 true）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "forgeops.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ForgeOpsTracingProperties.class)
public class ForgeOpsTracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AppVersionInfo forgeOpsAppVersionInfo(Environment environment,
                                                 ObjectProvider<BuildProperties> buildProperties) {
        return new AppVersionInfo(environment, buildProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestLogStore forgeOpsRequestLogStore(ForgeOpsTracingProperties properties) {
        return new RequestLogStore(properties.getTrackedRequests());
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestIdFilter forgeOpsRequestIdFilter(RequestLogStore requestLogStore, AppVersionInfo versionInfo) {
        return new RequestIdFilter(requestLogStore, versionInfo);
    }

    @Bean
    @ConditionalOnMissingBean
    public TracingEndpoints forgeOpsTracingEndpoints(AppVersionInfo versionInfo, RequestLogStore requestLogStore,
                                                     ForgeOpsTracingProperties properties) {
        return new TracingEndpoints(versionInfo, requestLogStore, properties);
    }
}
