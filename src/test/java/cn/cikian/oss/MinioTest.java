package cn.cikian.oss;


import cn.cikian.oss.enmus.OssTypeEnum;
import cn.cikian.oss.model.CikOssConfiguration;
import cn.cikian.oss.service.IOssService;
import cn.cikian.oss.service.impl.MinIOServiceImpl;
import cn.cikian.oss.service.OssServiceContext;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNotNull;

/**
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-05-14 22:53
 */

public class MinioTest {

    private CikOssConfiguration getConfig() {
        Dotenv dotenv = Dotenv.configure()
                .directory("./") // 指定 .env 所在目录
                .load();

        CikOssConfiguration config = new CikOssConfiguration();
        config.setEnable(true);
        config.setProvider(OssTypeEnum.MINIO);
        config.setEndpoint(dotenv.get("MINIO_ENDPOINT"));
        config.setUrlPrefix("http://140.143.140.103:9001");
        config.setBucket(dotenv.get("MINIO_BUCKET"));
        config.setAccessKey(dotenv.get("MINIO_ACCESS_KEY"));
        config.setSecretKey(dotenv.get("MINIO_SECRET_KEY"));
        config.setObjectDirPrefix("photos");
        config.setExpire(3600L);
        return config;
    }

    private OssServiceContext getContext() {
        CikOssConfiguration config = getConfig();
        IOssService minio = new MinIOServiceImpl(config);
        return new OssServiceContext(Collections.singletonList(minio), config);
    }

    @Test
    public void testUpload(){
        OssServiceContext context = getContext();
        assertNotNull(context.getOssService());
        String localPath = "C:\\Users\\Stargis\\temp-kmz\\ckmz_1778836058958.kmz";
        try (InputStream inputStream = Files.newInputStream(Paths.get(localPath))) {
            context.putObject("test/ckmz_1778836058958.kmz", inputStream);
        } catch (Exception e) {
            throw new RuntimeException("KMZ 文件上传 OSS 失败: " + e.getMessage(), e);
        }
    }

    @Test
    public void testGetUrl(){
        OssServiceContext context = getContext();
        assertNotNull(context.getOssService());
        URL objectUrl = context.getObjectUrl("test/ckmz_1778836058958.kmz");
        System.out.println(objectUrl);
        System.out.println(objectUrl.getPath());
        System.out.println(objectUrl.getHost());
    }

    @Test
    public void testGetObject(){
        OssServiceContext context = getContext();
        assertNotNull(context.getOssService());
        InputStream inputStream = context.getObject("test/ckmz_1778836058958.kmz");
        String targetPath = "D:\\Users\\Stargis\\Desktop\\savedTest" + System.currentTimeMillis() + ".kmz";
        try {
            Path path = Paths.get(targetPath);
            if (Files.notExists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("文件保存成功，路径为: " + targetPath);
        } catch (IOException e) {
            System.err.println("文件保存失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testObjectList(){
        OssServiceContext context = getContext();
        assertNotNull(context.getOssService());
        List<String> objectList = context.getObjectList("test/");
        System.out.println(objectList);
    }
}
