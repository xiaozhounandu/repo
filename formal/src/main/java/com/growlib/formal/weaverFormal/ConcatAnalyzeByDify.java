package com.growlib.formal.weaverFormal;

import okhttp3.*;
import okhttp3.RequestBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@CrossOrigin("*")
public class ConcatAnalyzeByDify {

    private static final Logger log = LoggerFactory.getLogger(ConcatAnalyzeByDify.class);

    // Dify API 配置
    private static final String BASE_URL = "https://api.dify.ai/v1";
    private static final String BEARER_TOKEN = "app-je0noX8o2pQydxjQBtYH27eJ";

    // 泛微下载基础 URL
    private static final String OA_BASE_URL = "http://oa.growlib.cn";

    // 数据库操作工具，由 Spring 自动注入
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // OkHttp 客户端，用于上传文件与调用 Dify 接口
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(6300, TimeUnit.SECONDS)
            .readTimeout(6300, TimeUnit.SECONDS)
            .build();

    // -----------------------------------------------------------------------
    // 核心接口
    // -----------------------------------------------------------------------

    /**
     * 自动上传接口: GET /api/auto-upload
     */
    @GetMapping("/api/auto-upload_file")
    public ResponseEntity<?> autoUpload(@RequestParam List<String> fieldId) {
        // ⚠️ 检查 JdbcTemplate 是否为 null
        if (jdbcTemplate == null) {
            log.error("【FATAL ERROR】JdbcTemplate 未注入！");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonMap("error", "数据库连接组件未初始化。"));
        }

        Map<String, Object> result = new HashMap<>();
        List<String> uploadFileIds = new ArrayList<>();
        String currentWeaverCookies = WeaverAuthController.getCurrentWeaverCookies();

        try {
            // 修正后的检查：如果 Cookie 为空，尝试登录一次。
            if (currentWeaverCookies.isEmpty()) {
                log.warn("Cookie 缺失，尝试触发一次登录...");
                WeaverAuthController authController = new WeaverAuthController();
                if (!authController.refreshWeaverCredentials()) {
                    log.error("【FATAL ERROR】Cookie 缺失且无法获取。");
                    result.put("error", "泛微登录失败，无法获取有效的下载 Cookie。请先访问 /api/auth/login 或检查配置。");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
                }
                currentWeaverCookies = WeaverAuthController.getCurrentWeaverCookies(); // 重新获取
            }


            List<Integer> allFileNumbers = new ArrayList<>();
            String sql = "SELECT IMAGEFILEID FROM docimagefile WHERE DOCID = ?";

            // 1. 遍历 fieldId，收集附件 fileid
            for (String fId : fieldId) {
                log.info("【DEBUG】开始查询附件，fieldId=" + fId);
                List<Integer> numbers = jdbcTemplate.queryForList(sql, Integer.class, fId.trim());
                log.info("【DEBUG】SQL 查询结果：fieldId=" + fId + " 对应附件ID列表=" + numbers);
                allFileNumbers.addAll(numbers);
            }

            if (allFileNumbers.isEmpty()) {
                log.info("【DEBUG ERROR】没有找到有效附件ID。");
                result.put("error", "没有有效附件可上传");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }

            // 2. 遍历附件 fileId → 下载 → 上传到 Dify
            for (int fileNum : allFileNumbers) {
                String downloadUrl = OA_BASE_URL +
                        "/weaver/weaver.file.FileDownload?fileid=" + fileNum +
                        "&download=1&requestid=-1&desrequestid=-1&loginidweaver=196";

                log.info("【DEBUG】开始尝试下载文件 fileid=" + fileNum + " from URL: " + downloadUrl);

                byte[] fileBytes = downloadFile(downloadUrl, currentWeaverCookies);

                log.info("【DEBUG】文件 fileid=" + fileNum + " 下载成功，大小: " + fileBytes.length + " bytes");

                String fileNameWithExt = "file_" + fileNum + ".pdf";

                String fileId = uploadFileToDify(fileBytes, fileNameWithExt);
                uploadFileIds.add(fileId);

                log.info("【DEBUG】文件 fileid=" + fileNum + " 上传 Dify 成功，Dify File ID: " + fileId);
            }

            if (uploadFileIds.isEmpty()) {
                result.put("error", "没有上传成功的文件，无法解析");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }

            // 3. 调用 Dify Chat 接口
            JSONObject chatJson = sendChatRequestMultiple(uploadFileIds);

            log.info("【DEBUG】Dify 解析请求发送成功。");

            // 4. 前端返回
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


    // -----------------------------------------------------------------------
    // 辅助方法
    // -----------------------------------------------------------------------

    /**
     * 下载泛微文件
     */
    private byte[] downloadFile(String urlStr, String cookies) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Cookie", cookies);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(300000);
        conn.setReadTimeout(300000  );

        int responseCode = conn.getResponseCode();

        if (responseCode != 200) {
            log.info("【ERROR】下载失败，URL: {} HTTP code: {}", urlStr, responseCode);
            throw new RuntimeException("下载失败，HTTP code=" + responseCode + "。请检查 Cookie 是否过期或文件是否存在。");
        }

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

        RequestBody fileBody = RequestBody.create(
                fileBytes,
                MediaType.parse("application/octet-stream")
        );

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
            log.info("【DEBUG】上传文件返回 body = " + body);

            if (!response.isSuccessful()) {
                throw new RuntimeException("文件上传失败: " + body);
            }

            JSONObject json = new JSONObject(body);
            if (!json.has("id") || json.getString("id").isEmpty()) {
                throw new RuntimeException("上传接口返回无效 fileId: " + body);
            }

            return json.getString("id");
        }
    }
//
//
//    /**
//     * 多文件 → 调用 Dify Chat 接口
//     */
//    private JSONObject sendChatRequestMultiple(List<String> uploadFileIds) throws Exception {
//        JSONObject req = new JSONObject();
//
//        req.put("query", "请总结一下这些文件内容");
//        req.put("user", "abc-123");
//        req.put("response_mode", "blocking");
//
//        JSONArray fileArray = new JSONArray();
//        for (String fileId : uploadFileIds) {
//            JSONObject fileObj = new JSONObject();
//            fileObj.put("type", "document");
//            fileObj.put("transfer_method", "local_file");
//            fileObj.put("upload_file_id", fileId);
//            fileArray.put(fileObj);
//        }
//
//        JSONObject inputs = new JSONObject();
//        inputs.put("file", fileArray);
//        req.put("inputs", inputs);
//
//        RequestBody body = RequestBody.create(
//                req.toString(),
//                MediaType.parse("application/json")
//        );
//
//        Request request = new Request.Builder()
//                .url(BASE_URL + "/chat-messages")
//                .addHeader("Authorization", "Bearer " + BEARER_TOKEN)
//                .post(body)
//                .build();
//
//        try (Response response = client.newCall(request).execute()) {
//            String respBody = response.body().string();
//            return new JSONObject(respBody);
//        }
//    }
//}

    /**
     * 多文件 → 调用 Dify Chat 接口
     */
    private JSONObject sendChatRequestMultiple(List<String> uploadFileIds) throws Exception {
        JSONObject req = new JSONObject();

        req.put("query", "请总结一下这些文件内容");
        req.put("user", "abc-123");
        req.put("response_mode", "blocking");

        JSONArray fileArray = new JSONArray();
        for (String fileId : uploadFileIds) {
            JSONObject fileObj = new JSONObject();
            fileObj.put("type", "document");
            fileObj.put("transfer_method", "local_file");
            fileObj.put("upload_file_id", fileId);
            fileArray.put(fileObj);
        }

        JSONObject inputs = new JSONObject();
        inputs.put("file", fileArray);
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

            // ⚠️ 关键修正：检查 HTTP 状态码
            if (!response.isSuccessful()) {
                log.error("【Dify API ERROR】请求失败，HTTP Code: {}，Body: {}", response.code(), respBody);
                // 创建一个包含错误信息的 JSON 对象，这样外层 catch 到的异常信息就是 JSON 格式
                JSONObject errorJson = new JSONObject();
                errorJson.put("answer", "【Dify API 调用失败】HTTP 状态码: " + response.code());
                errorJson.put("detail", respBody);
                return errorJson;
            }

            // 修正后的日志，记录成功的响应体
            log.info("【Dify API SUCCESS】响应体: {}", respBody);

            // 尝试解析 JSON
            try {
                return new JSONObject(respBody);
            } catch (Exception e) {
                // 如果 body 成功但不是标准 JSON（极少情况），也返回一个错误信息 JSON
                log.error("【Dify JSON PARSE ERROR】响应体解析失败: {}", respBody, e);
                JSONObject errorJson = new JSONObject();
                errorJson.put("answer", "【Dify JSON 解析失败】响应体非标准 JSON。");
                errorJson.put("detail", respBody);
                return errorJson;
            }
        }
    }
}