package com.growlib.formal.weaverFormal;

import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/auth")
public class WeaverAuthController {

    private static final Logger log = LoggerFactory.getLogger(WeaverAuthController.class);

    // 泛微登录配置 (请根据实际情况修改)
    private static final String OA_BASE_URL = "http://oa.growlib.cn";
    private static final String LOGIN_URL = OA_BASE_URL + "/api/hrm/login/checkLogin";
//    替换为账号名称和密码
    private static final String OA_USERNAME = "weizhijie";
    private static final String OA_PASSWORD = "wzj@245960";

    // 动态存储的 Cookie 和 Token
    private static String currentWeaverCookies = "";
    private static String currentWeaverUserToken = "";

    // OkHttp 客户端，用于登录
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 对外公开：获取当前的 Cookie 和 Token。
     */
    public static String getCurrentWeaverCookies() {
        return currentWeaverCookies;
    }

    /**
     * 对外公开：尝试刷新泛微登录凭证。
     */
    public boolean refreshWeaverCredentials() {
        // ⚠️ 强制打印，确认方法开始执行
        System.out.println("【DEBUG-START】进入 refreshWeaverCredentials 方法...");
        log.info("【LOGIN】开始刷新泛微登录凭证...");

        String requestBodyStr = String.format("loginid=%s&userpassword=%s&isie=true", OA_USERNAME, OA_PASSWORD);

        log.info("【LOGIN DEBUG】Target URL: {}", LOGIN_URL);
        log.info("【LOGIN DEBUG】Request Body: {}", requestBodyStr);

        RequestBody requestBody = RequestBody.create(
                requestBodyStr,
                MediaType.parse("application/x-www-form-urlencoded; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(LOGIN_URL)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                .header("Accept", "*/*")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("User-Agent", "OkHttp Custom Client")
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            int statusCode = response.code();
            String responseBody = response.body().string();

            log.info("【LOGIN DEBUG】HTTP Status Code: {}", statusCode);
            System.out.println("【DEBUG-RESPONSE】HTTP Status Code: " + statusCode); // 强制打印状态码

            if (!response.isSuccessful()) {
                log.error("【LOGIN ERROR】HTTP 状态码错误: {}，Body: {}", statusCode, responseBody);
                return false;
            }

            if (responseBody.contains("\"loginstatus\":\"true\"") && responseBody.contains("\"user_token\"")) {

                JSONObject json = new JSONObject(responseBody);
                currentWeaverUserToken = json.getString("user_token");

                StringBuilder cookieBuilder = new StringBuilder();
                List<String> setCookieHeaders = response.headers().values("Set-Cookie");

                if (!setCookieHeaders.isEmpty()) {
                    Pattern pattern = Pattern.compile("(JSESSIONID|ecology_JSessionid)=[^;]+");

                    for (String cookieHeader : setCookieHeaders) {
                        Matcher matcher = pattern.matcher(cookieHeader);

                        while (matcher.find()) {
                            if (cookieBuilder.length() > 0) {
                                cookieBuilder.append("; ");
                            }
                            cookieBuilder.append(matcher.group());
                        }
                    }
                }

                currentWeaverCookies = cookieBuilder.toString();
                log.info("【LOGIN SUCCESS】Cookie 刷新成功。Token: {}..., Cookies: {}",
                        currentWeaverUserToken.substring(0, Math.min(10, currentWeaverUserToken.length())),
                        currentWeaverCookies);
                System.out.println("【DEBUG-END】refreshWeaverCredentials 成功返回 true"); // 强制打印成功
                return true;
            } else {
                log.error("【LOGIN ERROR】API 登录失败，响应体: {}", responseBody);
                return false;
            }
        } catch (Exception e) {
            // ⚠️ 强制记录异常的字符串形式
            log.error("【LOGIN EXCEPTION】登录过程中发生异常。异常类型和消息: {}", e.toString());
            e.printStackTrace();
            System.err.println("【DEBUG-FAILURE】捕获到异常，返回 false: " + e.getMessage());

            return false;
        }
    }

    /**
     * GET /api/auth/login: 手动触发登录并获取状态。
     */
    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> manualLoginCheck() {
        if (refreshWeaverCredentials()) {
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "泛微登录成功。",
                    "token_preview", currentWeaverUserToken.substring(0, Math.min(10, currentWeaverUserToken.length())) + "...",
                    "cookie_length", String.valueOf(currentWeaverCookies.length())
            ));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "status", "FAILURE",
                    "message", "泛微登录失败。请检查日志中的异常堆栈和配置。"
            ));
        }
    }
}