package org.soft.softrear.pojo.dto.dify;

import java.util.Map;

public class DronePipelineResult {
    private String mode;
    private Map<String, Object> detection;
    private DifyDecisionResult dify;
    private DifyDecisionResult agent;

    public DronePipelineResult(String mode, Map<String, Object> detection, DifyDecisionResult dify) {
        this.mode = mode;
        this.detection = detection;
        this.dify = dify;
        this.agent = dify;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Map<String, Object> getDetection() {
        return detection;
    }

    public void setDetection(Map<String, Object> detection) {
        this.detection = detection;
    }

    public DifyDecisionResult getDify() {
        return dify;
    }

    public void setDify(DifyDecisionResult dify) {
        this.dify = dify;
        this.agent = dify;
    }

    public DifyDecisionResult getAgent() {
        return agent;
    }

    public void setAgent(DifyDecisionResult agent) {
        this.agent = agent;
        this.dify = agent;
    }
}
