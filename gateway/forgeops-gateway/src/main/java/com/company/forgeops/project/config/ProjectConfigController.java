package com.company.forgeops.project.config;

import com.company.forgeops.project.registry.ProjectConfig;
import com.company.forgeops.project.registry.ProjectRegistryService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** 项目配置查询（§9.3）：新项目接入 = Registry 加 YAML。 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectConfigController {

    private final ProjectRegistryService registry;

    public ProjectConfigController(ProjectRegistryService registry) {
        this.registry = registry;
    }

    @GetMapping("/{projectId}/config")
    public ProjectConfig config(@PathVariable String projectId) {
        return registry.find(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目未注册: " + projectId));
    }

    @GetMapping
    public List<String> list() {
        return registry.all().stream().map(ProjectConfig::id).toList();
    }

    @org.springframework.web.bind.annotation.PostMapping("/reload")
    public Map<String, Object> reload() {
        registry.reload();
        return Map.of("projects", list());
    }
}
