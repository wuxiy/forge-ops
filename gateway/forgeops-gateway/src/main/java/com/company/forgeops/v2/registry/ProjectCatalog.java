package com.company.forgeops.v2.registry;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

/** Parses every project before replacing the visible catalog; failed refreshes leave the last valid catalog intact. */
@Service
public class ProjectCatalog {

    private static final Set<String> TOP_LEVEL_FIELDS = Set.of("id", "enabled", "repositoryRoot", "allowedPaths",
            "browserOrigins", "policy");
    private static final Set<String> POLICY_FIELDS = Set.of("autoMerge", "productionDeploy");

    private final RegistryProperties properties;
    private final AtomicReference<Map<String, ResolvedProject>> projects = new AtomicReference<>(Map.of());

    public ProjectCatalog(RegistryProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void loadAtStartup() {
        reload();
    }

    public void reload() {
        Path registryPath = absoluteDirectory(properties.getPath(), "registry path");
        Path workspaceRoot = absoluteDirectory(properties.getWorkspaceRoot(), "workspace root");
        Map<String, ResolvedProject> parsed = new LinkedHashMap<>();
        try (var files = Files.list(registryPath)) {
            List<Path> yamlFiles = files.filter(file -> file.getFileName().toString().endsWith(".yaml"))
                    .sorted(Comparator.comparing(Path::toString)).toList();
            if (yamlFiles.isEmpty()) {
                throw new IllegalStateException("registry has no project yaml files: " + registryPath);
            }
            for (Path file : yamlFiles) {
                ResolvedProject project = parse(file, workspaceRoot);
                if (parsed.putIfAbsent(project.id(), project) != null) {
                    throw new IllegalStateException("duplicate project id: " + project.id());
                }
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("cannot read registry: " + registryPath, failure);
        }
        projects.set(Map.copyOf(parsed));
    }

    public ResolvedProject require(String projectId) {
        return resolve(projectId).orElseThrow(() -> new IllegalArgumentException("unknown enabled project: " + projectId));
    }

    public Optional<ResolvedProject> resolve(String projectId) {
        return Optional.ofNullable(projects.get().get(projectId));
    }

    private ResolvedProject parse(Path file, Path workspaceRoot) {
        Map<String, Object> root;
        try (InputStream stream = Files.newInputStream(file)) {
            Object loaded = new Yaml().load(stream);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IllegalStateException(file + " must contain a YAML object");
            }
            root = stringKeyed(map, file + " root");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("cannot read project registry file: " + file, failure);
        }
        rejectUnknown(root, TOP_LEVEL_FIELDS, file.toString());
        String id = requireString(root, "id", file);
        boolean enabled = requireBoolean(root, "enabled", file);
        if (!enabled) {
            throw new IllegalStateException("disabled projects do not belong in the v2 runtime registry: " + file);
        }
        Path repositoryRoot = resolveInside(workspaceRoot, requireString(root, "repositoryRoot", file), "repositoryRoot", file);
        List<Path> allowedPaths = requireStringList(root, "allowedPaths", file).stream()
                .map(path -> resolveInside(repositoryRoot, path, "allowedPaths", file)).toList();
        Set<String> browserOrigins = Set.copyOf(requireStringList(root, "browserOrigins", file));
        if (browserOrigins.stream().anyMatch(origin -> !origin.matches("https?://[^/]+(:\\d+)?"))) {
            throw new IllegalStateException("browserOrigins must use an origin only: " + file);
        }
        Map<String, Object> policy = requireObject(root, "policy", file);
        rejectUnknown(policy, POLICY_FIELDS, file + " policy");
        if (requireBoolean(policy, "autoMerge", file) || requireBoolean(policy, "productionDeploy", file)) {
            throw new IllegalStateException("project policy cannot relax the global human gate: " + file);
        }
        return new ResolvedProject(id, repositoryRoot, List.copyOf(allowedPaths), browserOrigins);
    }

    private static Path absoluteDirectory(String value, String field) {
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException(field + " is not a directory: " + path);
        }
        return path;
    }

    private static Path resolveInside(Path base, String value, String field, Path file) {
        Path resolved = base.resolve(value).normalize();
        if (!resolved.startsWith(base) || !Files.exists(resolved)) {
            throw new IllegalStateException(field + " must exist inside " + base + ": " + file);
        }
        return resolved;
    }

    private static Map<String, Object> stringKeyed(Map<?, ?> source, String label) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalStateException(label + " has a non-string key");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> requireObject(Map<String, Object> root, String field, Path file) {
        if (!(root.get(field) instanceof Map<?, ?> map)) {
            throw new IllegalStateException(field + " must be an object: " + file);
        }
        return stringKeyed(map, field);
    }

    private static String requireString(Map<String, Object> root, String field, Path file) {
        Object value = root.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException(field + " must be a non-blank string: " + file);
        }
        return text;
    }

    private static boolean requireBoolean(Map<String, Object> root, String field, Path file) {
        Object value = root.get(field);
        if (!(value instanceof Boolean bool)) {
            throw new IllegalStateException(field + " must be a boolean: " + file);
        }
        return bool;
    }

    private static List<String> requireStringList(Map<String, Object> root, String field, Path file) {
        Object value = root.get(field);
        if (!(value instanceof Collection<?> values) || values.isEmpty() || values.stream()
                .anyMatch(item -> !(item instanceof String text) || text.isBlank())) {
            throw new IllegalStateException(field + " must be a non-empty string list: " + file);
        }
        return values.stream().map(String.class::cast).toList();
    }

    private static void rejectUnknown(Map<String, Object> fields, Set<String> allowed, String label) {
        Set<String> unknown = new LinkedHashSet<>(fields.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("unknown fields in " + label + ": " + unknown);
        }
    }
}
