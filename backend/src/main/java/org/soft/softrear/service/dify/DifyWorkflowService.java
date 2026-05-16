package org.soft.softrear.service.dify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.soft.softrear.config.DifyProperties;
import org.soft.softrear.pojo.dto.dify.DifyDecisionResult;
import org.soft.softrear.service.agent.AgentDecisionService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class DifyWorkflowService implements AgentDecisionService {

    private final DifyProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DifyWorkflowService(DifyProperties properties) {
        this.properties = properties;
        this.restTemplate = createRestTemplate();
    }

    @Override
    public DifyDecisionResult runDecision(Map<String, Object> inputs, String userSeed) {
        Map<String, Object> status = probeStatus();
        if (!Boolean.TRUE.equals(status.get("ready"))) {
            return DifyDecisionResult.skipped(String.valueOf(status.getOrDefault("probeMessage", "Dify is unavailable")));
        }
        Map<String, Object> request = new HashMap<>();
        request.put("inputs", inputs);
        request.put("response_mode", "blocking");
        request.put("user", buildUser(userSeed));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    buildRunUrl(),
                    new HttpEntity<>(request, headers),
                    Map.class
            );
            return parseResponse(response.getBody());
        } catch (RestClientResponseException e) {
            return DifyDecisionResult.failed(readFriendlyError(e));
        } catch (RestClientException e) {
            return DifyDecisionResult.failed(readFriendlyError(e));
        }
    }

    @Override
    public Map<String, Object> probeStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", getProviderName());
        status.put("enabled", properties.isEnabled());
        status.put("baseUrl", properties.getBaseUrl());
        status.put("apiKeyConfigured", StringUtils.hasText(properties.getApiKey()));
        status.put("workflowIdConfigured", StringUtils.hasText(properties.getWorkflowId()));
        status.put("workflowUserPrefix", properties.getWorkflowUserPrefix());
        status.put("online", false);
        status.put("ready", false);

        if (!properties.isEnabled()) {
            status.put("probeMessage", "Dify workflow is disabled");
            return status;
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            status.put("probeMessage", "Dify API key is not configured");
            return status;
        }
        if (!StringUtils.hasText(properties.getWorkflowId())) {
            status.put("probeMessage", "Dify workflow id is not configured");
            return status;
        }

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    buildInfoUrl(),
                    HttpMethod.GET,
                    new HttpEntity<>(createAuthHeaders()),
                    Map.class
            );
            Map<String, Object> body = response.getBody() == null ? new HashMap<>() : response.getBody();
            String mode = asString(body.get("mode"));
            status.put("online", true);
            status.put("appName", asString(body.get("name")));
            status.put("appMode", mode);
            status.put("ready", "workflow".equalsIgnoreCase(mode));
            status.put("probeMessage", "Dify reachable");
            return status;
        } catch (RestClientResponseException e) {
            status.put("probeMessage", readFriendlyError(e));
            return status;
        } catch (RestClientException e) {
            status.put("probeMessage", readFriendlyError(e));
            return status;
        }
    }

    @Override
    public String getProviderName() {
        return "dify";
    }

    private DifyDecisionResult parseResponse(Map<String, Object> body) {
        if (body == null) {
            return DifyDecisionResult.failed("Dify returned empty response");
        }

        Map<String, Object> data = asMap(body.get("data"));
        Map<String, Object> outputs = data == null ? asMap(body.get("outputs")) : asMap(data.get("outputs"));
        if (outputs == null) {
            outputs = new HashMap<>();
        }

        DifyDecisionResult result = new DifyDecisionResult();
        result.setWorkflowRunId(asString(firstNonNull(body.get("workflow_run_id"), data == null ? null : data.get("id"))));
        result.setStatus(asString(firstNonNull(data == null ? null : data.get("status"), body.get("status"), "succeeded")));
        result.setRawOutputs(outputs);
        result.setValidated(asBoolean(outputs.get("validated")));
        result.setReason(asString(outputs.get("reason")));
        result.setCommand(asMap(outputs.get("command")));
        result.setDispatchStatus(asInteger(outputs.get("dispatch_status")));
        result.setDispatchResponse(asString(outputs.get("dispatch_response")));
        result.setError(asString(firstNonNull(data == null ? null : data.get("error"), body.get("error"))));
        return result;
    }

    private String buildUser(String userSeed) {
        String seed = StringUtils.hasText(userSeed) ? userSeed : "anonymous";
        return properties.getWorkflowUserPrefix() + "-" + seed;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "http://localhost/v1";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String buildRunUrl() {
        String base = normalizeBaseUrl(properties.getBaseUrl());
        return base + "/workflows/" + properties.getWorkflowId() + "/run";
    }

    private String buildInfoUrl() {
        return normalizeBaseUrl(properties.getBaseUrl()) + "/info";
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getApiKey());
        return headers;
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return new RestTemplate(factory);
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return parseJsonObject(text);
        }
        return null;
    }

    private Map<String, Object> parseJsonObject(String text) {
        String normalized = unwrapJsonText(text);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return objectMapper.readValue(normalized, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private String unwrapJsonText(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                trimmed = trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        return trimmed;
    }

    private String readFriendlyError(RestClientException exception) {
        String message = exception.getMessage();
        if (exception instanceof RestClientResponseException responseException) {
            String body = responseException.getResponseBodyAsString();
            if (StringUtils.hasText(body)) {
                if (body.contains("media_url") && body.contains("less than 512")) {
                    return "Dify 输入校验失败：媒体引用过长，已改用短引用后重试";
                }
                if (body.contains("invalid_param") && body.contains("message")) {
                    return "Dify 输入参数校验失败";
                }
                return body;
            }
            if (StringUtils.hasText(message)) {
                return message;
            }
            return "Dify 调用失败";
        }
        return StringUtils.hasText(message) ? message : "Dify 调用失败";
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
