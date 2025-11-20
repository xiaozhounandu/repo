package com.growlib.demo.test;

import okhttp3.*;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@CrossOrigin("*")
@RestController
public class DifyProxyFileApplication {

    private static final String BASE_URL = "http://154.8.226.18:7777/v1";
    private static final String BEARER_TOKEN = "app-t6nqXhG1n41scFcSmPLkJBsR"; // 替换为有效 token

    OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(300, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build();

    @GetMapping("/api/auto-upload")
    public ResponseEntity<Map<String, Object>> autoUploadAndParse() {
        Map<String, Object> result = new HashMap<>();
        String downloadUrl = "http://oa.growlib.cn/weaver/weaver.file.FileDownload?fileid=44186&download=1&requestid=-1&desrequestid=-1&loginidweaver=196";
        String cookies = "JSESSIONID=aaahbZD8v0Z5o_duDgxFz; ecology_JSessionid=aaahbZD8v0Z5o_duDgxFz;"; // 替换为有效 Cookie

        InputStream fileStream = null;
        try {
            System.out.println("开始下载文件...");
            fileStream = downloadFile(downloadUrl, cookies);
            System.out.println("文件下载成功");

            System.out.println("开始上传到 Dify...");
            String uploadFileId = uploadFileToDify(fileStream, "contract.pdf");
            System.out.println("文件上传成功，fileId=" + uploadFileId);

            if (uploadFileId == null) {
                result.put("error", "文件上传失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }

            System.out.println("调用 chat-messages API 解析文件...");
            JSONObject chatJson = sendChatRequest(uploadFileId);

            result.put("upload_file_id", uploadFileId);
            result.put("raw_response", chatJson.toString(2));
            if (chatJson.has("answer")) {
                result.put("answer_text", chatJson.getString("answer"));
            }
            if (chatJson.has("answer_json")) {
                result.put("answer_json", chatJson.getJSONObject("answer_json").toMap());
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            result.put("error", "操作失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        } finally {
            if (fileStream != null) {
                try {
                    fileStream.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private InputStream downloadFile(String urlStr, String cookies) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", cookies);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("下载失败, HTTP code=" + code);
        }
        return conn.getInputStream();
    }

    private String uploadFileToDify(InputStream inputStream, String filename) throws IOException {
        RequestBody fileBody = new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/octet-stream");
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    sink.write(buffer, 0, bytesRead);
                }
            }
        };

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, fileBody)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/files/upload")
                .addHeader("Authorization", "Bearer " + BEARER_TOKEN)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body().string();
            System.out.println("Dify Upload Response: " + body);
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(body);
                return json.optString("id", null);
            } else {
                throw new IOException("文件上传失败 code=" + response.code() + " body=" + body);
            }
        }
    }

    private JSONObject sendChatRequest(String uploadFileId) throws IOException {
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
            String respBody = response.body().string();
            System.out.println("chat-messages Response: " + respBody);

            JSONObject rawJson = new JSONObject(respBody);

            // 提取 answer 中 JSON 部分
            if (rawJson.has("answer")) {
                String answerStr = rawJson.getString("answer");
                // 正则提取 ```json {…} ``` 部分
                String jsonPart = answerStr.replaceAll("(?s)```json\\s*(\\{.*?})\\s*```.*", "$1");
                try {
                    JSONObject answerJson = new JSONObject(jsonPart);
                    rawJson.put("answer_json", answerJson);
                } catch (Exception e) {
                    // 提取失败也不影响返回
                    System.out.println("解析 answer_json 失败: " + e.getMessage());
                }
            }

            return rawJson;
        }
    }
}
