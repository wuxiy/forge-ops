package com.company.forgeops.v2.integration.inbox;

import com.company.forgeops.v2.integration.domain.IntegrationEventState;
import java.util.UUID;

public record InboxReceipt(UUID eventId, IntegrationEventState state, boolean duplicate) {
}
