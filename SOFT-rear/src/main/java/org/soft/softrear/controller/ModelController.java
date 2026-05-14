package org.soft.softrear.controller;

import org.soft.softrear.pojo.ResponseMessage;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/model")
public class ModelController {
    
    @PostMapping("/chat")
    public ResponseMessage<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        try {
            String message = (String) request.get("message");
            
            // 这里调用大模型API
            // 实际项目中应该调用具体的大模型服务
            String response = "大模型回复: " + message;
            
            Map<String, Object> result = new HashMap<>();
            result.put("response", response);
            result.put("model", "local-model");
            
            return ResponseMessage.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseMessage<>(500, "大模型调用失败", null);
        }
    }
    
    @PostMapping("/generate")
    public ResponseMessage<Map<String, Object>> generate(@RequestBody Map<String, Object> request) {
        try {
            String prompt = (String) request.get("prompt");
            
            // 这里调用大模型生成API
            String generatedContent = "生成内容: " + prompt;
            
            Map<String, Object> result = new HashMap<>();
            result.put("content", generatedContent);
            result.put("model", "local-model");
            
            return ResponseMessage.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseMessage<>(500, "生成失败", null);
        }
    }
}
