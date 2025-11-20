package com.growlib.demo;

import okhttp3.*;
import okhttp3.RequestBody;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@CrossOrigin("*")
@RestController
public class DifyController {
    private static final String BASE_URL = "http://154.8.226.18:7777/v1";
    private static final String BEARER_TOKEN = "app-t6nqXhG1n41 scFcSmPLkJBsR"; // ← 换成你的 token
//    private static final OkHttpClient client = new OkHttpClient();

    OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(320, TimeUnit.SECONDS) // 连接超时 20 秒
            .readTimeout(360, TimeUnit.SECONDS)    // 读取超时 60 秒
            .build();


    @PostMapping("/uploadAndChat")
    public Map<String, Object> uploadAndChat(@RequestParam("file") MultipartFile multipartFile) throws IOException {
        Map<String, Object> result = new HashMap<>();

        // 1️⃣ 上传文件
        String uploadFileId = uploadFile(multipartFile);
        if (uploadFileId == null) {
            result.put("error", "文件上传失败");
            return result;
        }

        // 2️⃣ 聊天总结
        String chatResponse = sendChatRequest(uploadFileId);
        System.out.println("chatResponse = " + chatResponse);
        JSONObject json = new JSONObject(chatResponse);

        result.put("upload_file_id", uploadFileId);

        result.put("raw_response", json.toString(2));

        if (json.has("answer")) {
            result.put("answer", json.getString("answer"));
        }

        return result;
    }

    private String uploadFile(MultipartFile multipartFile) throws IOException {
        RequestBody fileBody = RequestBody.create(
                multipartFile.getBytes(),
                MediaType.parse("application/octet-stream")
        );

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", multipartFile.getOriginalFilename(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/files/upload")
                .addHeader("Authorization", "Bearer " + BEARER_TOKEN)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body().string();
            System.out.println("Upload Response: " + body);

            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(body);
                return json.optString("id", null);
            } else {
                System.err.println("文件上传失败 code=" + response.code());
                return null;
            }
        }
    }

    private String sendChatRequest(String uploadFileId) throws IOException {

        JSONObject requestBodyJson = new JSONObject();
        requestBodyJson.put("query", "请总结一下这个文件内容");
        requestBodyJson.put("user", "abc-123");
        requestBodyJson.put("response_mode", "blocking");

        JSONObject inputs = new JSONObject();
        JSONObject file = new JSONObject();
        file.put("type", "document");
        file.put("transfer_method", "local_file");
        file.put("upload_file_id", uploadFileId);
        inputs.put("file", file);
        requestBodyJson.put("inputs", inputs);

        RequestBody body = RequestBody.create(
                requestBodyJson.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(BASE_URL + "/chat-messages")
                .addHeader("Authorization", "Bearer " + BEARER_TOKEN)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
}
