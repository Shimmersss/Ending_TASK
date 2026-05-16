package org.soft.softrear.controller;

import org.soft.softrear.pojo.ResponseMessage;
import org.soft.softrear.pojo.dto.dify.DronePipelineResult;
import org.soft.softrear.service.agent.AgentDecisionService;
import org.soft.softrear.service.dify.DetectionPipelineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/external")
public class ExternalServiceController {

    private final DetectionPipelineService detectionPipelineService;
    private final AgentDecisionService agentDecisionService;

    public ExternalServiceController(DetectionPipelineService detectionPipelineService,
                                     AgentDecisionService agentDecisionService) {
        this.detectionPipelineService = detectionPipelineService;
        this.agentDecisionService = agentDecisionService;
    }

    @GetMapping("/agent/status")
    public ResponseMessage<Map<String, Object>> getAgentStatus() {
        return ResponseMessage.success(agentDecisionService.probeStatus());
    }

    @GetMapping("/dify/status")
    public ResponseMessage<Map<String, Object>> getDifyStatus() {
        return getAgentStatus();
    }

    @PostMapping("/dify/drone-pipeline")
    public ResponseMessage<DronePipelineResult> runDronePipeline(
            @RequestParam("mediaType") String mediaType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "calibFile", required = false) MultipartFile calibFile,
            @RequestParam(value = "imagePathHint", required = false) String imagePathHint,
            @RequestParam(value = "conf", required = false) Double conf,
            @RequestParam(value = "iou", required = false) Double iou,
            @RequestParam(value = "scoreThr", required = false) Double scoreThr,
            @RequestParam(value = "missionContext", required = false) String missionContext,
            @RequestParam(value = "droneId", required = false) String droneId,
            @RequestParam(value = "includeDify", required = false, defaultValue = "true") boolean includeDify) {
        try {
            return ResponseMessage.success(detectionPipelineService.run(
                    mediaType,
                    file,
                    imageFile,
                    calibFile,
                    imagePathHint,
                    conf,
                    iou,
                    scoreThr,
                    missionContext,
                    droneId,
                    includeDify
            ));
        } catch (IllegalArgumentException e) {
            return new ResponseMessage<>(400, e.getMessage(), null);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseMessage<>(500, "Drone pipeline failed: " + e.getMessage(), null);
        }
    }

    @PostMapping("/agent/chat")
    public ResponseMessage<Map<String, Object>> chatWithAgent(@RequestBody Map<String, Object> request) {
        String message = String.valueOf(request.getOrDefault("message", ""));
        String userId = String.valueOf(request.getOrDefault("userId", "demo-user"));
        String mediaUrl = shortMediaRef(String.valueOf(request.getOrDefault("mediaUrl", "chat-only")));

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("media_url", mediaUrl);
        inputs.put("media_type", String.valueOf(request.getOrDefault("mediaType", "image")));
        inputs.put("mission_context", message);
        inputs.put("drone_id", String.valueOf(request.getOrDefault("droneId", "demo-drone-001")));

        Map<String, Object> response = new HashMap<>();
        response.put("response", agentDecisionService.getProviderName() + " workflow result");
        response.put("provider", agentDecisionService.getProviderName());
        Object decision = agentDecisionService.runDecision(inputs, userId);
        response.put("agent", decision);
        response.put("dify", decision);
        return ResponseMessage.success(response);
    }

    @PostMapping("/dify/chat")
    public ResponseMessage<Map<String, Object>> chatWithDify(@RequestBody Map<String, Object> request) {
        return chatWithAgent(request);
    }

    private String shortMediaRef(String value) {
        if (value == null) {
            return "chat-only";
        }
        String normalized = value.trim().replaceAll("[^a-zA-Z0-9._:-]", "_");
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120);
    }

    @PostMapping("/python/process-image")
    public ResponseMessage<Map<String, Object>> processImageWithPython(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("result", "Python service placeholder: " + request.get("processType") + " - " + request.get("imagePath"));
        response.put("service", "python-image-processing");
        return ResponseMessage.success(response);
    }
}
