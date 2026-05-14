package org.soft.softrear.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DifyProperties {

    @Value("${dify.base-url:http://localhost/v1}")
    private String baseUrl;

    @Value("${dify.api-key:}")
    private String apiKey;

    @Value("${dify.workflow-id:}")
    private String workflowId;

    @Value("${dify.workflow-user-prefix:drone-demo}")
    private String workflowUserPrefix;

    @Value("${dify.enabled:true}")
    private boolean enabled;

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getWorkflowUserPrefix() {
        return workflowUserPrefix;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
