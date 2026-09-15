package com.company.forgeops.v2.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectCatalogTest {

    @TempDir
    Path temp;

    @Test
    void rejectsInvalidRefreshAndKeepsTheLastCompleteCatalog() throws Exception {
        Path registry = Files.createDirectory(temp.resolve("registry"));
        Files.createDirectory(temp.resolve("repo"));
        Path project = registry.resolve("pilot.yaml");
        Files.writeString(project, valid("pilot"));
        ProjectCatalog catalog = new ProjectCatalog(properties(registry));
        catalog.reload();

        assertTrue(catalog.require("pilot").allowsPath(temp.resolve("repo").resolve("src")));
        assertEquals("pilot", catalog.resolveGitHubRepository("example/pilot").orElseThrow().id());

        Files.writeString(project, valid("pilot").replace("autoMerge: false", "autoMerge: true"));
        assertThrows(IllegalStateException.class, catalog::reload);
        assertEquals("pilot", catalog.require("pilot").id());
    }

    @Test
    void rejectsUnknownFieldsAndPathsOutsideTheRepository() throws Exception {
        Path registry = Files.createDirectory(temp.resolve("registry"));
        Files.createDirectory(temp.resolve("repo"));
        Files.writeString(registry.resolve("pilot.yaml"), valid("pilot") + "unexpected: value\n");
        ProjectCatalog catalog = new ProjectCatalog(properties(registry));

        assertThrows(IllegalStateException.class, catalog::reload);
    }

    private RegistryProperties properties(Path registry) {
        RegistryProperties properties = new RegistryProperties();
        properties.setPath(registry.toString());
        properties.setWorkspaceRoot(temp.toString());
        return properties;
    }

    private static String valid(String id) {
        return """
                id: %s
                enabled: true
                repositoryRoot: repo
                allowedPaths:
                  - .
                browserOrigins:
                  - https://pilot.example
                policy:
                  autoMerge: false
                  productionDeploy: false
                github:
                  repository: example/%s
                  baseBranch: main
                  allowedMergeLogins:
                    - forgeops-owner
                  testEnvironment: test
                """.formatted(id, id);
    }
}
