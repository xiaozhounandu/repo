//package com.example.demo.demos.web;
//
//
//import okhttp3.*;
//import okhttp3.RequestBody;
//import org.json.JSONArray;
//import org.json.JSONObject;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.web.bind.annotation.*;
//
//import java.io.InputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.*;
//import java.util.concurrent.TimeUnit;
//
//@CrossOrigin("*")
//@RestController
//public class DifyProxyFileApplication {
//
//    private static final String BASE_URL = "https://api.dify.ai/v1";
//    private static final String BEARER_TOKEN = "app-u3S8XIseReSTmCjYMlALwtFD";
//
//    @Autowired
//    private JdbcTemplate jdbcTemplate;
//
//    OkHttpClient client = new OkHttpClient.Builder()
//            .connectTimeout(1300, TimeUnit.SECONDS)
//            .readTimeout(1300, TimeUnit.SECONDS)
//            .build();
//
//    /**
//     * 🔥 支持多个 fieldId: /api/auto-upload?fieldId=58435&fieldId=58436
//     */
//    @GetMapping("/api/auto-upload")
//    public ResponseEntity<?> autoUpload(@RequestParam List<String> fieldId) {
//        Map<String, Object> result = new HashMap<>();
//        List<String> uploadFileIds = new ArrayList<>();
//
//        try {
//            // 1. 遍历每个 fieldId，查询真实文件编号
//            List<Integer> allFileNumbers = new ArrayList<>();
//
//
//            String sql = "SELECT b.id FROM uf_components a " +
//                    "LEFT JOIN docimagefile b ON a.wdfj = b.DOCID " +
//                    "WHERE a.wdfj = ?";
//
//            for (String fId : fieldId) {
//                if (!fId.matches("\\d+")) {
//                    throw new IllegalArgumentException("非法 fieldId: " + fId);
//                }
//
//                List<Integer> numbers = jdbcTemplate.queryForList(sql, Integer.class, fId);
//            }
//
//            String cookies = "aaaeLo5EWxoSH7u8pixFz; ecology_JSessionid=aaaeLo5EWxoSH7u8pixFz;";
//
//            // 2. 下载 + 上传文件
//            for (int fileNum : allFileNumbers) {
//                String downloadUrl =
//                        "http://oa.growlib.cn/weaver/weaver.file.FileDownload?fileid="
//                                + fileNum + "&download=1&requestid=-1&desrequestid=-1&loginidweaver=196";
//
//                byte[] fileBytes = downloadFile(downloadUrl, cookies);
//
//                Path path = Paths.get("./downloaded_file_" + fileNum);
//                Files.write(path, fileBytes);
//
//                String fileId = uploadFileToDify(fileBytes, "file_" + fileNum + ".pdf");
//                uploadFileIds.add(fileId);
//            }
//
//            // 3. 解析多个文件
//            JSONObject chatJson = sendChatRequestMultiple(uploadFileIds);
//
//            result.put("upload_file_ids", uploadFileIds);
//            result.put("raw_response", chatJson.toString(2));
//
//            if (chatJson.has("answer"))
//                result.put("answer_text", chatJson.getString("answer"));
//
//            if (chatJson.has("answer_json"))
//                result.put("answer_json", chatJson.getJSONObject("answer_json").toMap());
//
//            return ResponseEntity.ok(result);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            result.put("error", "操作失败: " + e.getMessage());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
//        }
//    }
//
//    /** 下载 OA 文件 */
//    private byte[] downloadFile(String urlStr, String cookies) throws Exception {
//        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
//        conn.setRequestMethod("GET");
//        conn.setRequestProperty("Cookie", cookies);
//        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
//        conn.setConnectTimeout(30000);
//        conn.setReadTimeout(30000);
//
//        if (conn.getResponseCode() != 200)
//            throw new RuntimeException("下载失败，HTTP code=" + conn.getResponseCode());
//
//        try (InputStream is = conn.getInputStream()) {
//            byte[] fileBytes = is.readAllBytes();
//
//            System.out.println("前 200 字节预览 = \n" +
//                    new String(fileBytes, 0, Math.min(200, fileBytes.length)));
//
//            return fileBytes;
//        }
//    }
//
//    /** 上传文件到 Dify */
//    private String uploadFileToDify(byte[] fileBytes, String filename) throws Exception {
//        RequestBody fileBody =
//                RequestBody.create(fileBytes, MediaType.parse("application/pdf"));
//
//        MultipartBody requestBody = new MultipartBody.Builder()
//                .setType(MultipartBody.FORM)
//                .addFormDataPart("file", filename, fileBody)
//                .build();
//
//        Request request = new Request.Builder()
//                .url(BASE_URL + "/files/upload")
//                .addHeader("Authorization", "Bearer " + BEARER_TOKEN)
//                .post(requestBody)
//                .build();
//
//        try (Response response = client.newCall(request).execute()) {
//            String body = response.body().string();
//            if (response.isSuccessful()) {
//                return new JSONObject(body).optString("id", null);
//            } else {
//                throw new RuntimeException("文件上传失败: " + body);
//            }
//        }
//    }
//
//    /** 调用 Dify 解析多个文件 */
//    private JSONObject sendChatRequestMultiple(List<String> uploadFileIds) throws Exception {
//
//        JSONObject req = new JSONObject();
//        req.put("query", "请总结一下这些文件内容");
//        req.put("user", "abc-123");
//        req.put("response_mode", "blocking");
//
//        JSONArray fileArray = new JSONArray();
//        for (String fileId : uploadFileIds) {
//            JSONObject f = new JSONObject();
//            f.put("type", "document");
//            f.put("transfer_method", "local_file");
//            f.put("upload_file_id", fileId);
//            fileArray.put(f);
//        }
//
//        JSONObject inputs = new JSONObject();
//        inputs.put("file", fileArray);
//        req.put("inputs", inputs);
//
//        RequestBody body =
//                RequestBody.create(req.toString(), MediaType.parse("application/json"));
//
//        Request request = new Request.Builder()
//                .url(BASE_URL + "/chat-messages")
//                .addHeader("Authorization", "Bearer " + BEARER_TOKEN)
//                .post(body)
//                .build();
//
//        Response response = client.newCall(request).execute();
//        String respBody = response.body().string();
//        JSONObject rawJson = new JSONObject(respBody);
//
//        // 尝试提取 answer_json
//        if (rawJson.has("answer")) {
//            String answerStr = rawJson.getString("answer");
//
//            try {
//                String jsonPart = answerStr.replaceAll("(?s).*?(\\{.*}).*", "$1");
//                JSONObject answerJson = new JSONObject(jsonPart);
//                rawJson.put("answer_json", answerJson);
//            } catch (Exception ignored) {}
//        }
//
//        return rawJson;
//    }
//}