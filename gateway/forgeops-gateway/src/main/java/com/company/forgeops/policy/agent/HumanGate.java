package com.company.forgeops.policy.agent;

import org.springframework.stereotype.Component;

/**
 * Agent 自动化边界（§4/§15.1/§19.2）：
 * 自动化终点 = Draft PR；Merge 与发布永远需要人工 —— 平台侧硬编码，不读配置、不可放宽。
 */
@Component
public class HumanGate {

    public boolean agentCanMerge() {
        return false;
    }

    public boolean agentCanDeploy() {
        return false;
    }

    /** 任何携带 agent merge 语义的回调一律拒绝。 */
    public void assertMergeByHuman(String actorType) {
        if ("AGENT".equalsIgnoreCase(actorType)) {
            throw new IllegalArgumentException("策略禁止：Agent 不允许执行 Merge（Human Gate）");
        }
    }
}
