package com.growlib.demo;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public class OaBatchDownloader {

    /**
     * 批量下载 OA 附件
     *
     * @param fileInfos  每个元素包含 fileid 和原始文件名
     * @param jsessionid 登录 OA 的 session
     * @param saveDir    保存目录
     * @throws Exception
     */
    public static void downloadAttachments(List<Map<String, String>> fileInfos, String jsessionid, String saveDir) throws Exception {
        for (Map<String, String> fileInfo : fileInfos) {
            String fileId = fileInfo.get("fileid");
            String fileName = fileInfo.get("filename");

            // 拼接下载 URL
            String url = "http://oa.growlib.cn/weaver/weaver.file.FileDownload?fileid=" + fileId + "&download=1";

            System.out.println("开始下载：" + fileName);
            downloadFile(url, jsessionid, saveDir + File.separator + fileName);
            System.out.println("下载完成：" + fileName);
        }
    }

    /**
     * 下载单个文件
     */
    private static void downloadFile(String downloadUrl, String jsessionid, String savePath) throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet(downloadUrl);
        get.setHeader("Cookie", "JSESSIONID=" + jsessionid);

        try (CloseableHttpResponse response = client.execute(get)) {
            int status = response.getStatusLine().getStatusCode();
            if (status != 200) {
                throw new RuntimeException("下载失败，HTTP 状态码：" + status);
            }

            InputStream inputStream = response.getEntity().getContent();

            // 确保目录存在
            File file = new File(savePath);
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) parentDir.mkdirs();

            try (BufferedInputStream bis = new BufferedInputStream(inputStream);
                 FileOutputStream fos = new FileOutputStream(file)) {

                byte[] buffer = new byte[8192];
                int len;
                while ((len = bis.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.flush();
            }
            inputStream.close();
        } finally {
            client.close();
        }
    }

    // 测试示例
    public static void main(String[] args) throws Exception {
        String jsessionid = "aaaA8cKL1AqIadfLoexFz"; // OA 登录 session
        String saveDir = "/Users/weizhijie/Desktop/dify";

        // 假设前端传来两个附件
        List<Map<String, String>> fileInfos = new ArrayList<>();
        Map<String, String> f1 = new HashMap<>();
        f1.put("fileid", "43979");
        f1.put("filename", "合同.html");
        fileInfos.add(f1);


        downloadAttachments(fileInfos, jsessionid, saveDir);
    }
}
