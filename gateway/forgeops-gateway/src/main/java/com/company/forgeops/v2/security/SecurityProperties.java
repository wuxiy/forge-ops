package com.company.forgeops.v2.security;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Required 2.0 security configuration. No development default is intentionally provided. */
@Validated
@ConfigurationProperties("forgeops.v2.security")
public class SecurityProperties {

    @NotBlank
    private String tokenSecret;

    private Duration tokenTtl = Duration.ofMinutes(10);

    public String getTokenSecret() { return tokenSecret; }
    public void setTokenSecret(String tokenSecret) { this.tokenSecret = tokenSecret; }
    public Duration getTokenTtl() { return tokenTtl; }
    public void setTokenTtl(Duration tokenTtl) { this.tokenTtl = tokenTtl; }
}
