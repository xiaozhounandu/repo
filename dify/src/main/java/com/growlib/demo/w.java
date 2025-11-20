package com.growlib.demo;

import okhttp3.*;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class w {

    // Dify API 基础配置
    private static final String BASE_URL = "http://154.8.226.18:7777/v1";
    private static final String BEARER_TOKEN = "Bearer app-t6nqXhG1n41scFcSmPLkJBsR"; // ← 换成你的 Dify API Key

    // OkHttpClient 带超时配置
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public static void main(String[] args) throws IOException {
        String filePath = "/Users/weizhijie/Desktop/房屋租赁合同.docx"; // 本地要上传的文件路径

        // 1️⃣ 上传文件
        String fileId = uploadFile(filePath);

        if (fileId != null) {
            System.out.println("上传成功，文件ID: " + fileId);

            // 2️⃣ 调用 chat 接口
            sendChatRequest(fileId);
        } else {
            System.err.println("文件上传失败，无法继续。");
        }
    }

    // 上传文件到 Dify
    private static String uploadFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("文件不存在: " + file.getAbsolutePath());
            return null;
        }

        RequestBody fileBody = RequestBody.create(MediaType.parse("application/octet-stream"), file);

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(BASE_URL + "/files/upload")
                .addHeader("Authorization", BEARER_TOKEN)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body().string();
            System.out.println("Upload Response: " + body);

            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(body);
                return json.optString("id", null);
            } else {
                System.err.println("文件上传失败，HTTP状态码：" + response.code());
                return null;
            }
        }
    }

    // 调用 /v1/chat-messages 接口让 AI 读取文件
    private static void sendChatRequest(String uploadFileId) throws IOException {
        // 构造 inputs → file
        JSONObject fileJson = new JSONObject();
        fileJson.put("type", "document");
        fileJson.put("transfer_method", "local_file");
        fileJson.put("upload_file_id", uploadFileId);

        JSONObject inputs = new JSONObject();
        inputs.put("file", fileJson);

        // 构造请求体
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("query", "请总结一下这个文件内容");
        jsonBody.put("user", "abc-123");
        jsonBody.put("inputs", inputs);
        jsonBody.put("response_mode", "blocking");

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                jsonBody.toString()
        );

        Request request = new Request.Builder()
                .url(BASE_URL + "/chat-messages")
                .addHeader("Authorization", BEARER_TOKEN)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        System.out.println("正在向 Dify 发起 chat 请求...");

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Chat 请求失败：" + response);
            } else {
                System.out.println("Chat Response: " + response.body().string());
            }
        }
    }
}
