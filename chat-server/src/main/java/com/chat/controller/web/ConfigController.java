package com.chat.controller.web;

import com.chat.config.FileServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ConfigController {

    @Autowired
    private FileServerConfig fileServerConfig;

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("fileServerUrl", fileServerConfig.getServerUrl());
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", config);
        
        return ResponseEntity.ok(result);
    }
}