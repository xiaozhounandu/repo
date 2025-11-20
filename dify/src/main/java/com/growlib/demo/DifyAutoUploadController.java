package com.growlib.demo;


import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@RestController
@CrossOrigin(origins = "*")
public class DifyAutoUploadController {

    private static final String DIFY_UPLOAD_URL = "https://api.dify.ai/v1/files/upload";
    private static final String API_KEY = "app-q9O1i3HGHkgMSAH7NJxfCO0G";
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/api/download-and-upload")
    public ResponseEntity<String> downloadAndUpload() {
        String downloadUrl = "http://oa.growlib.cn/aweaver/weaver.file.FileDownload?fileid=44186&download=1&requestid=-1&desrequestid=-1&loginidweaver=196";
        try {
            // 下载文件到内存
            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cookie", "JSESSIONID=aaahbZD8v0Z5o_duDgxFz; ecology_JSessionid=aaahbZD8v0Z5o_duDgxFz;");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            InputStream in = conn.getInputStream();

            // 构造上传请求
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new MultipartInputStreamFileResource(in, "downloaded_file.pdf"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(API_KEY);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(DIFY_UPLOAD_URL, requestEntity, String.class);

            in.close();
            conn.disconnect();

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"操作失败: " + e.getMessage() + "\"}");
        }
    }
}
