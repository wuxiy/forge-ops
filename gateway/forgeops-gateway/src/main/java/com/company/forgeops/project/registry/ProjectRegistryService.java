package com.company.forgeops.project.registry;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

/**
 * Project Registry（§10）：目录内 YAML = 项目接入唯一动作。
 * 新项目接入 = 放入一份 project.yaml，不需要改平台代码。
 */
@Service
public class ProjectRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ProjectRegistryService.class);

    private final Path registryDir;
    private final Map<String, ProjectConfig> cache = new ConcurrentHashMap<>();

    public ProjectRegistryService(@Value("${forgeops.registry.path}") String registryPath) {
        this.registryDir = Path.of(registryPath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void loadAll() {
        reload();
    }

    public synchronized void reload() {
        Map<String, ProjectConfig> fresh = new LinkedHashMap<>();
        if (Files.isDirectory(registryDir)) {
            try (Stream<Path> files = Files.list(registryDir)) {
                files.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                        .sorted()
                        .forEach(p -> {
                            try {
                                ProjectConfig config = parse(p);
                                fresh.put(config.id(), config);
                            } catch (Exception e) {
                                log.error("解析 Project Registry 失败: {}", p, e);
                            }
                        });
            } catch (IOException e) {
                log.error("读取 Registry 目录失败: {}", registryDir, e);
            }
        } else {
            log.warn("Registry 目录不存在: {}", registryDir);
        }
        cache.clear();
        cache.putAll(fresh);
        log.info("Project Registry 加载完成: {} -> {}", registryDir, cache.keySet());
    }

    @SuppressWarnings("unchecked")
    private ProjectConfig parse(Path file) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (var reader = Files.newBufferedReader(file)) {
            root = yaml.load(reader);
        }
        if (root == null) {
            throw new IllegalStateException("空配置文件: " + file);
        }

        Map<String, Object> frontend = asMap(root.get("frontend"));
        Map<String, Object> backend = asMap(root.get("backend"));
        Map<String, Object> observability = asMap(root.get("observability"));
        Map<String, Object> logs = asMap(observability.get("logs"));
        Map<String, Object> multica = asMap(root.get("multica"));

        ProjectConfig.RepoConfig frontendConfig = frontend == null ? null : new ProjectConfig.RepoConfig(
                str(frontend.get("framework")), str(frontend.get("repo")), str(frontend.get("repoProvider")),
                str(frontend.get("path")), str(frontend.get("defaultBranch")));
        ProjectConfig.RepoConfig backendConfig = backend == null ? null : new ProjectConfig.RepoConfig(
                str(backend.get("framework")), str(backend.get("repo")), str(backend.get("repoProvider")),
                str(backend.get("path")), str(backend.get("defaultBranch")));
        ProjectConfig.Observability obs = observability.isEmpty() ? null : new ProjectConfig.Observability(
                new ProjectConfig.Observability.Logs(
                        str(logs.get("type")), str(logs.get("service")), str(logs.get("baseUrl"))));
        ProjectConfig.MulticaConfig multicaConfig = multica.isEmpty() ? null : new ProjectConfig.MulticaConfig(
                str(multica.get("workspace")), str(multica.get("project")),
                str(multica.get("triageAgent")), str(multica.get("codingAgent")));

        return new ProjectConfig(
                str(root.get("id")),
                str(root.get("name")),
                asMap(root.get("feedback")),
                frontendConfig,
                backendConfig,
                asMap(root.get("runtime")),
                obs,
                multicaConfig,
                asMap(root.get("ci")),
                asMap(root.get("environments")),
                asMap(root.get("policy")),
                asMap(root.get("security")));
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>();
            m.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public Optional<ProjectConfig> find(String projectId) {
        return Optional.ofNullable(cache.get(projectId));
    }

    public List<ProjectConfig> all() {
        return List.copyOf(cache.values());
    }
}
