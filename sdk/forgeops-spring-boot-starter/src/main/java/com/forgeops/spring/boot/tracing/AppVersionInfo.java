package com.forgeops.spring.boot.tracing;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;

/**
 * 应用版本信息：优先读宿主应用 Maven build-info（BuildProperties），
 * 回退 spring.application.name / "dev"。
 */
public class AppVersionInfo {

    private final String serviceName;
    private final String version;
    private final String commit;

    public AppVersionInfo(Environment environment, ObjectProvider<BuildProperties> buildProperties) {
        this.serviceName = environment.getProperty("spring.application.name", "unknown-app");
        BuildProperties props = buildProperties == null ? null : buildProperties.getIfAvailable();
        if (props != null) {
            this.version = props.getVersion() == null ? "dev" : props.getVersion();
            String c = props.get("commit");
            this.commit = c == null ? "unknown" : c;
        } else {
            this.version = "dev";
            this.commit = "unknown";
        }
    }

    public String serviceName() { return serviceName; }
    public String version() { return version; }
    public String commit() { return commit; }
}
