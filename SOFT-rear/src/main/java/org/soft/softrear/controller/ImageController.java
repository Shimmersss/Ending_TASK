package org.soft.softrear.controller;

import org.soft.softrear.pojo.ResponseMessage;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/image")
public class ImageController {
    
    private static final String UPLOAD_DIR = "upload";
    
    public ImageController() {
        // 创建上传目录
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    @PostMapping("/upload")
    public ResponseMessage<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return new ResponseMessage<>(400, "文件为空", null);
        }
        
        try {
            // 生成唯一文件名
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            String filePath = UPLOAD_DIR + File.separator + fileName;
            
            // 保存文件
            File dest = new File(filePath);
            file.transferTo(dest);
            
            // 返回文件路径
            return ResponseMessage.success("/" + UPLOAD_DIR + "/" + fileName);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseMessage<>(500, "文件上传失败", null);
        }
    }
    
    @PostMapping("/process")
    public ResponseMessage<String> processImage(@RequestParam("imagePath") String imagePath, 
                                             @RequestParam("processType") String processType) {
        try {
            // 这里调用图像处理服务
            // 实际项目中应该调用Python服务
            String result = "处理成功: " + processType + " - " + imagePath;
            return ResponseMessage.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseMessage<>(500, "图像处理失败", null);
        }
    }
}
