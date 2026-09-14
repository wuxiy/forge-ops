package com.company.forgeops.v2.registry;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Paths are deliberately supplied by the deployment environment, never guessed from a source checkout. */
@Validated
@ConfigurationProperties("forgeops.v2.registry")
public class RegistryProperties {

    @NotBlank
    private String path;

    @NotBlank
    private String workspaceRoot;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getWorkspaceRoot() { return workspaceRoot; }
    public void setWorkspaceRoot(String workspaceRoot) { this.workspaceRoot = workspaceRoot; }
}
