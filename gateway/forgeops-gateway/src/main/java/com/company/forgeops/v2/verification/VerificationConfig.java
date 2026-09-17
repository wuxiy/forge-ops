package com.company.forgeops.v2.verification;

import com.company.forgeops.v2.registry.GlobalQualityPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires verification-layer beans that are not component-scanned services. */
@Configuration
public class VerificationConfig {

    @Bean
    public GlobalQualityPolicy globalQualityPolicy(VerificationProperties properties) {
        return properties.toGlobalQualityPolicy();
    }
}
