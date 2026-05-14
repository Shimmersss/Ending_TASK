package org.soft.softrear.pojo.dto.dify;

import java.util.Map;

public class DifyDecisionResult {
    private String workflowRunId;
    private String status;
    private Boolean validated;
    private String reason;
    private Map<String, Object> command;
    private Integer dispatchStatus;
    private String dispatchResponse;
    private Map<String, Object> rawOutputs;
    private String error;

    public static DifyDecisionResult skipped(String reason) {
        DifyDecisionResult result = new DifyDecisionResult();
        result.setStatus("skipped");
        result.setReason(reason);
        return result;
    }

    public static DifyDecisionResult failed(String error) {
        DifyDecisionResult result = new DifyDecisionResult();
        result.setStatus("failed");
        result.setError(error);
        return result;
    }

    public String getWorkflowRunId() {
        return workflowRunId;
    }

    public void setWorkflowRunId(String workflowRunId) {
        this.workflowRunId = workflowRunId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getValidated() {
        return validated;
    }

    public void setValidated(Boolean validated) {
        this.validated = validated;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Object> getCommand() {
        return command;
    }

    public void setCommand(Map<String, Object> command) {
        this.command = command;
    }

    public Integer getDispatchStatus() {
        return dispatchStatus;
    }

    public void setDispatchStatus(Integer dispatchStatus) {
        this.dispatchStatus = dispatchStatus;
    }

    public String getDispatchResponse() {
        return dispatchResponse;
    }

    public void setDispatchResponse(String dispatchResponse) {
        this.dispatchResponse = dispatchResponse;
    }

    public Map<String, Object> getRawOutputs() {
        return rawOutputs;
    }

    public void setRawOutputs(Map<String, Object> rawOutputs) {
        this.rawOutputs = rawOutputs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
