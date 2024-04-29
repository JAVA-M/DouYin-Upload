package com.mxf;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author manxingfu
 * @date 2024/4/28 16:58
 * @desc
 */
public class DouYinPublish {

    public static final String PATH = "C:\\Users\\sinan\\Desktop\\video";
	public static final String COOKIE_PATH = "XXX";
    public static final String URL = "http://192.168.20.201:18001";

    public static void main(String[] args) {
        DouYinPublish douYinPublish = new DouYinPublish();

        // 1、从日志中下载视频
        douYinPublish.downloadVideo();

        // 2、发布视频至抖音平台
        douYinPublish.autoUploadVideoToDouYin();
    }

    public void downloadVideo() {
        HashMap<String, String> map = new HashMap<>();
        List<String> files = findFiles(PATH + "\\log\\", ".log");
        for (String file : files) {
            map.putAll(findRelevantLines(PATH + "\\log\\" + file));
        }

        // 拿到所有的视频信息，下载视频
        map.forEach((title, url) -> {
            System.out.printf("开始下载[%s]: %s\n", title, url);
            download(url, title);
        });
        System.out.println("共下载" + map.size() + "个视频");
    }

    public HashMap<String, String> findRelevantLines(String filePath) {
        HashMap<String, String> map = new HashMap<>();
        String tag = "com.sinandata.content.mapper.FinReportVideoMapper.insert [137] -| ==> Parameters: ";
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(tag)) {
                    try {
                        String[] item = line.split(tag)[1].replaceAll("\\(String\\)", "").split(", ");
                        map.put(item[0].replace("\\N", ""), URL + item[1]);
                    } catch (Exception e) {
                        System.out.println("处理失败：" + tag);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return map;
    }

    /**
     * 自动上传视频至抖音
     */
    public void autoUploadVideoToDouYin() {

        List<String> videos = findFiles(PATH, ".mp4");
        if (videos.isEmpty()) {
            System.out.println("无视频数据，故结束发布");
            return;
        }
        System.out.println("今日预计发布：" + videos);
        System.setProperty("webdriver.chrome.driver", "D:\\Environment\\chrome_driver\\123\\chromedriver.exe");

        // 创建 ChromeDriver
        WebDriver driver = new ChromeDriver();
        driver.get("https://creator.douyin.com/creator-micro/content/upload");

        // 设置cookie免登录
        List<Cookie> cookies = getDouYinCookie(COOKIE_PATH);
        for (Cookie cookie : cookies) {
            driver.manage().addCookie(cookie);
        }

        // 逐个视频发布
        for (String video : videos) {
            String title = video.substring(0, video.length() - 4);
            String tagContent = getTagContent(title); // 内容标签根据自己视频自定义
            System.out.println("开始发布: " + title + ": " + tagContent);
            boolean result = uploadSingleVideo(driver, PATH + "\\" + video, title, tagContent);
            if (result) {
                System.out.println(video + "：发布成功");
            } else {
                System.out.println(video + "：发布失败");
            }
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 上传单个视频至抖音平台
     * @param driver
     * @param title
     * @param content
     * @return
     */
    public boolean uploadSingleVideo(WebDriver driver, String filePath, String title, String content) {
        try {
            driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
            driver.get("https://creator.douyin.com/creator-micro/content/upload");

            driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
            // 选择上传的文件 //*[@id="root"]/div/div/div[3]/div/div[1]/div/div[1]/div/label
            WebElement fileInput = driver.findElement(By.xpath("//*[@id=\"root\"]/div/div/div[3]/div/div[1]/div/div[1]/div/label/input"));
            fileInput.sendKeys(filePath); 
            driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
            // 选择封面
            try {
                driver.findElement(By.xpath("//*[@id=\"root\"]/div/div/div[2]/div[1]/div[5]/div/div[2]/div[1]/div/div[1]/div/img")).click();
            } catch (Exception e) {
                System.out.println("第一次封面选择失败: " + e.getMessage());
            }
            TimeUnit.SECONDS.sleep(2);
            driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
            // 填写标题
            try {
                WebElement titleInput = driver.findElement(By.xpath("//*[@id=\"root\"]/div/div/div[2]/div[1]/div[2]/div/div/div/div[1]/div/div/input"));
                titleInput.sendKeys(title);
            } catch (Exception e) {
                System.out.println("填写标题失败: " + e.getMessage());
            }
            TimeUnit.SECONDS.sleep(2);
            driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
            // 填写内容标签
            try {
                WebElement contentInput = driver.findElement(By.xpath("//*[@id=\"root\"]/div/div/div[2]/div[1]/div[2]/div/div/div/div[2]/div"));
                contentInput.sendKeys(content);
            } catch (Exception e) {
                System.out.println("填写内容标签失败: " + e.getMessage());
            }
            TimeUnit.SECONDS.sleep(2);
            // 点击发表
            driver.findElement(By.xpath("//*[@id=\"root\"]/div/div/div[2]/div[1]/div[17]/button[1]")).click();
        } catch (Exception e) {
            System.out.println("上传失败: " + title + ", 发生异常: " + e.getMessage());
            return false;
        }
        return true;
    }

    public List<String> findFiles(String path, String suffix) {
        ArrayList<String> files = new ArrayList<>();
        Path startingDir = Paths.get(path); // 替换为你的目录路径
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(startingDir, "*" + suffix)) {
            for (Path entry : directoryStream) {
                files.add(String.valueOf(entry.getFileName()));
            }
        } catch (IOException | DirectoryIteratorException e) {
            e.printStackTrace();
        }
        return files;
    }

    public List<Cookie> getDouYinCookie(String filePath) {
        // 读取 JSON 文件内容
        String jsonString = null;
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(filePath));
            jsonString = new String(encoded);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        JSONArray jsonArray = JSONArray.of(JSONArray.parseArray(jsonString, JSONObject.class));
        List<Cookie> cookies = new ArrayList<>();
        ArrayList<JSONObject> jsonList = (ArrayList<JSONObject>) jsonArray.get(0);
        for (int i = 0; i < jsonList.size(); i++) {
            JSONObject json = jsonList.get(i);
            try {
                cookies.add(new Cookie(json.getString("name"), json.getString("value"), json.getString("domain"), json.getString("path"), json.getDate("expirationDate"), json.getBoolean("secure")));
            } catch (Exception e) {
                System.out.println("无法封装成对象：\n " + json.toJSONString());
                throw new RuntimeException(e);
            }
        }

        return cookies;
    }

    /**
     * 根据标题信息生成标签内容
     * @param title eg: 2024年04月26日银行行业日报.mp4
     * @return
     */
    public String getTagContent(String title) {
        StringBuilder sb = new StringBuilder();
        // ...可按自己的业务需求自定义内容标签的逻辑
        sb.append(" #股市 #金融 #财经");
        return sb.toString();
    }

    public String getYear(String dateStr) {
        String pattern = "(\\d{4}年)";

        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(dateStr);

        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    public static void download(String targetUrl, String title) {
        try {
            java.net.URL url = new URL(targetUrl);
            URLConnection connection = url.openConnection();
            InputStream is = connection.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(is);
            FileOutputStream fos = new FileOutputStream(PATH + "\\" + title + ".mp4");
            byte[] buffer = new byte[1024];
            int len = 0;
            while ((len = bis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            bis.close();
            is.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}