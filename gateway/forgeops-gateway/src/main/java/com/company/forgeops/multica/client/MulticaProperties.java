package com.company.forgeops.multica.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forgeops.multica")
public record MulticaProperties(String baseUrl, String token, String workspaceId, String appUrl) {
}
