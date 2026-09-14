package com.company.forgeops.v2.agent.execution;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Service credentials are deployment supplied and must never be logged. */
@Validated
@ConfigurationProperties("forgeops.v2.runtime")
public class RuntimeExecutionProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String serviceToken;

    private int connectTimeoutMillis = 3_000;

    private int requestTimeoutMillis = 15_000;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getServiceToken() { return serviceToken; }
    public void setServiceToken(String serviceToken) { this.serviceToken = serviceToken; }
    public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
    public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }
    public int getRequestTimeoutMillis() { return requestTimeoutMillis; }
    public void setRequestTimeoutMillis(int requestTimeoutMillis) { this.requestTimeoutMillis = requestTimeoutMillis; }
}
