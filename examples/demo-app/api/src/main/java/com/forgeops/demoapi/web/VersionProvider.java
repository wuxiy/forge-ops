package com.forgeops.demoapi.web;

import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

/** 暴露 version / commitSha（Maven build-info 生成，架构文档 §8.3）。 */
@Component
public class VersionProvider {

    private final BuildProperties buildProperties;

    public VersionProvider(org.springframework.beans.factory.ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties.getIfAvailable();
    }

    public String version() {
        return buildProperties == null ? "dev" : buildProperties.getVersion();
    }

    public String commit() {
        if (buildProperties == null) {
            return "unknown";
        }
        String commit = buildProperties.get("commit");
        return commit == null ? "unknown" : commit;
    }
}
