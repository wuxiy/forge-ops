package com.company.forgeops.v2.integration.outbox;

import com.company.forgeops.v2.agent.execution.AgentRunDispatcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Routes only durable AgentRun commands; all workflow state remains in ForgeOps. */
@Component
public class AgentRunOutboxPublisher implements OutboxPublisher {

    private final AgentRunDispatcher dispatcher;
    private final ObjectMapper json;

    public AgentRunOutboxPublisher(AgentRunDispatcher dispatcher, ObjectMapper json) {
        this.dispatcher = dispatcher;
        this.json = json;
    }

    @Override
    public void publish(OutboxMessage message) {
        if (!"AGENT_RUN_REQUESTED".equals(message.eventType())) {
            throw new IllegalArgumentException("Unsupported outbox event: " + message.eventType());
        }
        try {
            JsonNode payload = json.readTree(message.payloadJson());
            dispatcher.dispatch(UUID.fromString(payload.required("agentRunId").asString()));
        } catch (JacksonException | IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid agent run outbox payload", error);
        }
    }
}
