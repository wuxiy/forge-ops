package com.company.forgeops.v2.integration.github;

import jakarta.validation.constraints.AssertTrue;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** GitHub delivery is opt-in: no secret means the public webhook endpoint is unavailable. */
@Validated
@ConfigurationProperties("forgeops.v2.github")
public class GitHubWebhookProperties {

    private boolean enabled;
    private String webhookSecret;
    private URI apiBaseUrl = URI.create("https://api.github.com");
    private String apiToken;
    private String machineMergeToken;
    private String machineMergeLogin = "forgeops-machine";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public URI getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(URI apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }
    public String getMachineMergeToken() { return machineMergeToken; }
    public void setMachineMergeToken(String machineMergeToken) { this.machineMergeToken = machineMergeToken; }
    public String getMachineMergeLogin() { return machineMergeLogin; }
    public void setMachineMergeLogin(String machineMergeLogin) { this.machineMergeLogin = machineMergeLogin; }

    @AssertTrue(message = "GitHub webhook-secret and api-token are required when GitHub integration is enabled")
    public boolean isCredentialsConfiguredWhenEnabled() {
        return !enabled || (webhookSecret != null && !webhookSecret.isBlank()
                && apiToken != null && !apiToken.isBlank() && apiBaseUrl != null && apiBaseUrl.isAbsolute());
    }
}
