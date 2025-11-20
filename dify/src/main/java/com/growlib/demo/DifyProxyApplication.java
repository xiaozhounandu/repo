package com.growlib.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName DifyProxyApplication
 * @Description
 * @Author xiaozhounandu
 * @Date 2025-10-31-16-49
 */

@RestController
@CrossOrigin("*")
public class DifyProxyApplication {
    private static final String DIFY_API_URL = "https://api.dify.ai/v1/chat-messages";
    private static final String API_KEY = "app-q9O1i3HGHkgMSAH7NJxfCO0G"; // 后端安全存储


    @PostMapping("/api/chat")
    public ResponseEntity<String> chat(@RequestBody Map<String, Object> requestBody) {
        RestTemplate restTemplate = new RestTemplate();

        // 构建请求体
        Map<String, Object> body = new HashMap<>();
        body.put("query", requestBody.get("query"));
        body.put("inputs", new HashMap<>()); // 如果有额外输入，可以填
        body.put("response_mode", "blocking");
        body.put("user", "abc-123");
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(API_KEY);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        // 调用 Dify
        ResponseEntity<String> response = restTemplate.postForEntity(DIFY_API_URL, entity, String.class);

        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }
}