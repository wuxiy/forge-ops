package com.company.forgeops.v2.integration.github;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** GitHub delivery is opt-in: no secret means the public webhook endpoint is unavailable. */
@Validated
@ConfigurationProperties("forgeops.v2.github")
public class GitHubWebhookProperties {

    private boolean enabled;
    private String webhookSecret;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    @AssertTrue(message = "forgeops.v2.github.webhook-secret is required when GitHub integration is enabled")
    public boolean isSecretConfiguredWhenEnabled() {
        return !enabled || (webhookSecret != null && !webhookSecret.isBlank());
    }
}
