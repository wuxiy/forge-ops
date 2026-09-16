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

/** Graph V1 node: file, module, capability or test suite, anchored to a baseline commit (ADR-0007). */
@Entity
@Table(name = "verification_node")
public class VerificationNode {

    public enum Type { FILE, MODULE, CAPABILITY, TEST_SUITE }

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 16)
    private Type type;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "baseline_commit", nullable = false, length = 128)
    private String baselineCommit;

    @Column(name = "captured_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime capturedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    protected VerificationNode() {
    }

    public static VerificationNode capture(String projectId, Type type, String path, String name, String baselineCommit) {
        var node = new VerificationNode();
        node.id = UUID.randomUUID();
        node.projectId = Objects.requireNonNull(projectId, "projectId");
        node.type = Objects.requireNonNull(type, "type");
        node.path = Objects.requireNonNull(path, "path");
        node.name = Objects.requireNonNull(name, "name");
        node.baselineCommit = Objects.requireNonNull(baselineCommit, "baselineCommit");
        node.capturedAt = OffsetDateTime.now();
        node.createdAt = node.capturedAt;
        return node;
    }

    public UUID getId() { return id; }
    public String getProjectId() { return projectId; }
    public Type getType() { return type; }
    public String getPath() { return path; }
    public String getName() { return name; }
    public String getBaselineCommit() { return baselineCommit; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
