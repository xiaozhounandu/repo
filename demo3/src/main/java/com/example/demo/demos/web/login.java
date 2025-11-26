package com.example.demo.demos.web;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.util.Map;

public class login {

    public static void main(String[] args) {
        String username = "weizhijie";
        String password = "wzj@245960";

        String cookie = loginAndGetCookie(username, password);
        System.out.println("最终获取到的 Cookie = " + cookie);
    }

    public static String loginAndGetCookie(String username, String password) {
        try {
            // 第一步：GET 登录页拿初始 cookie
            Connection.Response loginPage = Jsoup.connect("http://oa.growlib.cn/login/Login.jsp")
                    .method(Connection.Method.GET)
                    .execute();

            Map<String, String> cookies = loginPage.cookies();

            // 第二步：POST 用户名密码
            Connection.Response res = Jsoup.connect("http://oa.growlib.cn/login/CheckLogin.jsp")
                    .cookies(cookies)
                    .method(Connection.Method.POST)
                    .data("username", username)
                    .data("password", password)
                    .followRedirects(true)
                    .execute();

            // 合并登录后的 cookie
            cookies.putAll(res.cookies());

            // 拼成字符串
            StringBuilder sb = new StringBuilder();
            cookies.forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));

            return sb.toString();

        } catch (Exception e) {
            System.out.println("登录失败：" + e.getMessage());
            return null;
        }
    }
}
