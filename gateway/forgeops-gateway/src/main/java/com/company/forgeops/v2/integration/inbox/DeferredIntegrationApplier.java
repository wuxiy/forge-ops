package com.company.forgeops.v2.integration.inbox;

import com.company.forgeops.v2.integration.domain.IntegrationEvent;
import org.springframework.stereotype.Component;

/** Fails safe until Phase 6 binds a verified Git/CI/Deployment evidence handler. */
@Component
public class DeferredIntegrationApplier implements IntegrationApplier {
    @Override
    public ApplyResult apply(IntegrationEvent event) {
        return ApplyResult.deferred("No verified handler is registered for " + event.getEventType());
    }
}
