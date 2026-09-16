package com.company.forgeops.v2.agent.execution;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * OPS-09: readiness must fail when the private Runtime boundary is unreachable. The probe expects the
 * service-token challenge (401); any other outcome or transport failure marks readiness DOWN.
 */
@Component("forgeopsRuntime")
public class RuntimeHealthIndicator implements HealthIndicator {

    private final RuntimeExecutionProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public RuntimeHealthIndicator(RuntimeExecutionProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            return Health.unknown().withDetail("reason", "runtime base-url is not configured").build();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl().replaceAll("/$", "")
                    + "/v1/runs/health-probe")).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 404) {
                return Health.up().withDetail("probe", response.statusCode()).build();
            }
            return Health.down().withDetail("status", response.statusCode()).build();
        } catch (Exception failure) {
            return Health.down().withDetail("error", failure.getClass().getSimpleName()).build();
        }
    }
}
