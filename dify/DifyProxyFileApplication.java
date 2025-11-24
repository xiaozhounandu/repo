package com.growlib.demo.test;

import okhttp3.*;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

@CrossOrigin("*")
@RestController
public class DifyProxyFileApplication {

    private static final String BASE_URL = "https://api.dify.ai/v1";
    private static final String BEARER_TOKEN = "app-u3S8XIseReSTmCjYMlALwtFD"; // 替换为有效 token

    OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(1300, TimeUnit.SECONDS)
            .readTimeout(1300, TimeUnit.SECONDS)
            .build();

    /** 多文件自动下载、上传并解析 */
    @GetMapping("/api/auto-upload")
    public ResponseEntity<Map<String, Object>> autoUploadAndParseMultiple(@RequestParam List<Integer> numbers) {
        Map<String, Object> result = new HashMap<>();
        List<String> uploadFileIds = new ArrayList<>();

        try {
            String cookies = "aaaeLo5EWxoSH7u8pixFz; ecology_JSessionid=aaaeLo5EWxoSH7u8pixFz;";

            // 1️⃣ 下载并上传每个文件
            for (int number : numbers) {
                String downloadUrl = "http://oa.growlib.cn/weaver/weaver.file.FileDownload"
                        + "?fileid=" + number
                        + "&download=1&requestid=-1&desrequestid=-1&loginidweaver=196";

                System.out.println("下载文件: " + number);
                byte[] fileBytes = downloadFile(downloadUrl, cookies);

                Path path = Paths.get("./downloaded_file_" + number);
                Files.write(path, fileBytes);
                System.out.println("文件已保存到本地: " + path);

                System.out.println("上传文件: " + number);
                String fileId = uploadFileToDify(fileBytes, "contract_" + number + ".pdf");
                uploadFileIds.add(fileId);
            }

            // 2️⃣ 调用 chat-messages 解析所有文件
            JSONObject chatJson = sendChatRequestMultiple(uploadFileIds);

            // 3️⃣ 构造返回结果
            result.put("upload_file_ids", uploadFileIds);
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
        }
    }

    /** 下载 OA 文件 */
    private byte[] downloadFile(String urlStr, String cookies) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", cookies);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("下载失败, HTTP code=" + conn.getResponseCode());
        }

        try (InputStream is = conn.getInputStream()) {
            byte[] fileBytes = is.readAllBytes();
            int peekLen = Math.min(200, fileBytes.length);
            System.out.println("前 200 字节预览 = \n" + new String(fileBytes, 0, peekLen));
            return fileBytes;
        }
    }

    /** 上传单个文件到 Dify */
    private String uploadFileToDify(byte[] fileBytes, String filename) throws IOException {
        RequestBody fileBody = RequestBody.create(fileBytes, MediaType.parse("application/pdf"));

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

    /** 调用 chat-messages 解析多个文件 */
    private JSONObject sendChatRequestMultiple(List<String> uploadFileIds) throws IOException {
        JSONObject requestBodyJson = new JSONObject();
        requestBodyJson.put("query", "请总结一下这些文件内容");
        requestBodyJson.put("user", "abc-123");
        requestBodyJson.put("response_mode", "blocking");

        JSONArray fileArray = new JSONArray();
        for (String fileId : uploadFileIds) {
            JSONObject file = new JSONObject();
            file.put("type", "document");
            file.put("transfer_method", "local_file");
            file.put("upload_file_id", fileId);
            fileArray.put(file);
        }

        JSONObject inputs = new JSONObject();
        inputs.put("file", fileArray);
        requestBodyJson.put("inputs", inputs);

        RequestBody body = RequestBody.create(requestBodyJson.toString(), MediaType.parse("application/json"));

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
            if (rawJson.has("answer")) {
                String answerStr = rawJson.getString("answer");
                String jsonPart = answerStr.replaceAll("(?s)```json\\s*(\\{.*?})\\s*```.*", "$1");
                try {
                    JSONObject answerJson = new JSONObject(jsonPart);
                    rawJson.put("answer_json", answerJson);
                } catch (Exception e) {
                    System.out.println("解析 answer_json 失败: " + e.getMessage());
                }
            }
            return rawJson;
        }
    }
}
