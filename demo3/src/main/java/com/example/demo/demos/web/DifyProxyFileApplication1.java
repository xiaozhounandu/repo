package com.example.demo.demos.web;

import okhttp3.*;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

@CrossOrigin("*")
@RestController
public class DifyProxyFileApplication1 {

//
    private static final String BASE_URL = "https://api.dify.ai/v1";
    private static final String BEARER_TOKEN = "app-u3S8XIseReSTmCjYMlALwtFD";


//    private static final String BASE_URL = "http://154.8.226.18:7777/v1";
//    private static final String BEARER_TOKEN = "app-t6nqXhG1n41scFcSmPLkJBsR";
    // 数据库操作工具，由 Spring 自动注入
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // OkHttp 客户端，用于上传文件与调用 Dify 接口
    OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(6300, TimeUnit.SECONDS)
            .readTimeout(6300, TimeUnit.SECONDS)
            .build();
    /**
     * 自动上传接口
     * 功能：根据多个 fieldId（组件ID）找出附件、下载附件、上传到 Dify，再触发解析
     */
    @GetMapping("/api/auto-upload")
    public ResponseEntity<?> autoUpload(@RequestParam List<String> fieldId) {
        Map<String, Object> result = new HashMap<>();
        List<String> uploadFileIds = new ArrayList<>();

        try {
            List<Integer> allFileNumbers = new ArrayList<>();

            // SQL 查询：根据 uf_components → docimagefile 反查附件 fileid
            String sql = "SELECT b.imagefileid FROM uf_components a " +
                    "LEFT JOIN docimagefile b ON a.wdfj = b.DOCID " +
                    "WHERE a.wdfj = ?";

            // 遍历所有 fieldId，收集它们对应的附件 fileid
            for (String fId : fieldId) {
                List<Integer> numbers = jdbcTemplate.queryForList(sql, Integer.class, fId);
                System.out.println("fieldId=" + fId + " 对应附件ID列表=" + numbers);

                if (numbers.isEmpty()) {
                    System.out.println("fieldId=" + fId + " 没有附件，跳过");
                    continue;
                }

                allFileNumbers.addAll(numbers);
            }

            if (allFileNumbers.isEmpty()) {
                result.put("error", "没有有效附件可上传");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }

            // 固定 Cookie，保证能够访问泛微的文件下载接口
            String cookies = "JSESSIONID=aaagu3y-lBfqbbrEejxFz; ecology_JSessionid=aaagu3y-lBfqbbrEejxFz;";

            // 遍历附件 fileId → 下载 → 上传到 Dify
            for (int fileNum : allFileNumbers) {
                // 拼接泛微的文件下载 URL
                String downloadUrl =
                        "http://oa.growlib.cn/weaver/weaver.file.FileDownload?fileid="
                                + fileNum + "&download=1&requestid=-1&desrequestid=-1&loginidweaver=196";

                // 从泛微下载文件
                byte[] fileBytes = downloadFile(downloadUrl, cookies);

                // 构造上传给 Dify 的文件名
                String fileNameWithExt = "file_" + fileNum + ".pdf";

                // 上传到 Dify → 得到 fileId
                String fileId = uploadFileToDify(fileBytes, fileNameWithExt);
                uploadFileIds.add(fileId);
            }

            if (uploadFileIds.isEmpty()) {
                result.put("error", "没有上传成功的文件，无法解析");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }

            // 调用 Dify 的聊天接口，触发解析
            JSONObject chatJson = sendChatRequestMultiple(uploadFileIds);

            // 前端返回
            result.put("upload_file_ids", uploadFileIds);
            result.put("raw_response", chatJson.toString(2));

            if (chatJson.has("answer"))
                result.put("answer_text", chatJson.getString("answer"));

            if (chatJson.has("answer_json"))
                result.put("answer_json", chatJson.getJSONObject("answer_json").toMap());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            result.put("error", "操作失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }


    /**
     * 下载泛微文件
     * @param urlStr 泛微下载地址
     * @param cookies 会话 Cookie
     */
    private byte[] downloadFile(String urlStr, String cookies) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", cookies);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        if (conn.getResponseCode() != 200)
            throw new RuntimeException("下载失败，HTTP code=" + conn.getResponseCode());

        // 将文件流全部读为字节数组
        try (InputStream is = conn.getInputStream()) {
            return is.readAllBytes();
        }
    }


    /**
     * 上传文件到 Dify
     */
    private String uploadFileToDify(byte[] fileBytes, String filename) throws Exception {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new RuntimeException("文件内容为空，无法上传：" + filename);
        }

        // 构造文件体
        RequestBody fileBody = RequestBody.create(
                fileBytes,
                MediaType.parse("application/octet-stream")
        );

        // multipart/form-data 方式构造请求
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
            System.out.println("上传文件返回 body = " + body);

            if (!response.isSuccessful()) {
                throw new RuntimeException("文件上传失败: " + body);
            }

            // API 返回的 JSON 中必须有 id
            JSONObject json = new JSONObject(body);
            if (!json.has("id") || json.getString("id").isEmpty()) {
                throw new RuntimeException("上传接口返回无效 fileId: " + body);
            }

            return json.getString("id");
        }
    }


    /**
     * 多文件 → 调用 Dify Chat 接口
     */
    private JSONObject sendChatRequestMultiple(List<String> uploadFileIds) throws Exception {
        JSONObject req = new JSONObject();

        req.put("query", "请总结一下这些文件内容");  // 提示词
        req.put("user", "abc-123");                 // 用户ID
        req.put("response_mode", "blocking");       // 阻塞模式

        // Dify 的文件结构要求
        JSONArray fileArray = new JSONArray();
        for (String fileId : uploadFileIds) {
            JSONObject fileObj = new JSONObject();
            fileObj.put("type", "document");         // 文件类型
            fileObj.put("transfer_method", "local_file");
            fileObj.put("upload_file_id", fileId);   // 上传后的 ID
            fileArray.put(fileObj);
        }

        JSONObject inputs = new JSONObject();
        inputs.put("file", fileArray);  // 关键点：key 必须叫 file
        req.put("inputs", inputs);

        RequestBody body = RequestBody.create(
                req.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(BASE_URL + "/chat-messages")
                .addHeader("Authorization", "Bearer " + BEARER_TOKEN)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String respBody = response.body().string();
            return new JSONObject(respBody);
        }
    }

}