package com.company.forgeops.v2.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Graph V1 directed edge with provenance (ADR-0007). */
@Entity
@Table(name = "verification_edge")
public class VerificationEdge {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "from_node_id", nullable = false, columnDefinition = "UUID")
    private UUID fromNodeId;

    @Column(name = "to_node_id", nullable = false, columnDefinition = "UUID")
    private UUID toNodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private VerificationProvenance provenance;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    protected VerificationEdge() {
    }

    public static VerificationEdge link(String projectId, UUID fromNodeId, UUID toNodeId,
            VerificationProvenance provenance) {
        var edge = new VerificationEdge();
        edge.id = UUID.randomUUID();
        edge.projectId = Objects.requireNonNull(projectId, "projectId");
        edge.fromNodeId = Objects.requireNonNull(fromNodeId, "fromNodeId");
        edge.toNodeId = Objects.requireNonNull(toNodeId, "toNodeId");
        edge.provenance = Objects.requireNonNull(provenance, "provenance");
        edge.createdAt = OffsetDateTime.now();
        return edge;
    }

    public UUID getId() { return id; }
    public String getProjectId() { return projectId; }
    public UUID getFromNodeId() { return fromNodeId; }
    public UUID getToNodeId() { return toNodeId; }
    public VerificationProvenance getProvenance() { return provenance; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
