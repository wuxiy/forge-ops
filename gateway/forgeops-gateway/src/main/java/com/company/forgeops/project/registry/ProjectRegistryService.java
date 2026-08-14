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
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

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

    private ProjectConfig parse(Path file) throws IOException {
        Yaml yaml = new Yaml(new Constructor(ProjectConfig.class, new LoaderOptions()));
        try (var reader = Files.newBufferedReader(file)) {
            return yaml.load(reader);
        }
    }

    public Optional<ProjectConfig> find(String projectId) {
        return Optional.ofNullable(cache.get(projectId));
    }

    public List<ProjectConfig> all() {
        return List.copyOf(cache.values());
    }
}
