package com.chat.controller.web;

import com.chat.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false, defaultValue = "general") String type) {
        
        try {
            FileUploadService.FileUploadResult result = fileUploadService.upload(file);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("filename", result.getFilename());
            response.put("originalName", result.getOriginalName());
            response.put("extension", result.getExtension());
            response.put("size", result.getSize());
            response.put("url", result.getUrl());
            response.put("type", type);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "文件上传失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/upload/image")
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        return upload(file, "image");
    }

    @PostMapping("/upload/video")
    public ResponseEntity<Map<String, Object>> uploadVideo(@RequestParam("file") MultipartFile file) {
        return upload(file, "video");
    }

    @PostMapping("/upload/audio")
    public ResponseEntity<Map<String, Object>> uploadAudio(@RequestParam("file") MultipartFile file) {
        return upload(file, "audio");
    }

    @PostMapping("/upload/file")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        return upload(file, "file");
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(
            @RequestParam("path") String path,
            @RequestParam(value = "filename", required = false) String filename) throws IOException {
        
        String relativePath = path.startsWith("/") ? path.substring(1) : path;
        
        if (!fileUploadService.fileExists(relativePath)) {
            return ResponseEntity.notFound().build();
        }
        
        FileUploadService.FileInfo fileInfo = fileUploadService.getFileInfo(relativePath);
        
        Resource resource = new FileSystemResource(fileInfo.getPath());
        
        String downloadFilename = filename != null ? filename : fileInfo.getFilename();
        
        MediaType mediaType = MediaType.parseMediaType(
            fileInfo.getContentType() != null ? fileInfo.getContentType() : "application/octet-stream"
        );
        
        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(fileInfo.getSize())
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + downloadFilename + "\"")
            .body(resource);
    }

    @GetMapping("/file/info")
    public ResponseEntity<Map<String, Object>> getFileInfo(@RequestParam("path") String path) throws IOException {
        String relativePath = path.startsWith("/") ? path.substring(1) : path;
        
        if (!fileUploadService.fileExists(relativePath)) {
            return ResponseEntity.notFound().build();
        }
        
        FileUploadService.FileInfo fileInfo = fileUploadService.getFileInfo(relativePath);
        
        Map<String, Object> result = new HashMap<>();
        result.put("filename", fileInfo.getFilename());
        result.put("size", fileInfo.getSize());
        result.put("contentType", fileInfo.getContentType());
        
        return ResponseEntity.ok(result);
    }
}