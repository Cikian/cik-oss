package cn.cikian.oss.service.impl;

import cn.cikian.oss.enmus.OssTypeEnum;
import cn.cikian.oss.exception.CikException;
import cn.cikian.oss.model.CikOssConfiguration;
import cn.cikian.oss.model.CredentialsToken;
import cn.cikian.oss.service.IOssService;
import com.upyun.RestManager;
import com.upyun.UpException;
import io.minio.*;
import io.minio.credentials.AssumeRoleProvider;
import io.minio.credentials.Credentials;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 又拍云 存储实现
 * 支持 Spring Boot 自动装配及原生 Java 手动实例化
 */
public class UpyunServiceImpl implements IOssService {

    private static final Logger log = LoggerFactory.getLogger(UpyunServiceImpl.class);

    private RestManager client;

    private final CikOssConfiguration configuration;

    /**
     * 构造函数注入配置
     *
     * @param configuration 非静态配置对象
     */
    public UpyunServiceImpl(CikOssConfiguration configuration) {
        this.configuration = configuration;
        this.createClient();
    }

    @Override
    public OssTypeEnum getOssType() {
        return OssTypeEnum.UPYUN;
    }

    @Override
    public CredentialsToken getCredentials() {
        try {
            // 使用实例变量 configuration 获取参数
            AssumeRoleProvider provider = new AssumeRoleProvider(
                    configuration.getEndpoint(),
                    configuration.getAccessKey(),
                    configuration.getSecretKey(),
                    Math.toIntExact(configuration.getExpire()),
                    null,
                    configuration.getRegion(),
                    null, null, null, null);

            Credentials credential = provider.fetch();
            return new CredentialsToken(
                    credential.accessKey(),
                    credential.secretKey(),
                    credential.sessionToken(),
                    configuration.getExpire());
        } catch (NoSuchAlgorithmException e) {
            log.error("获取 MinIO STS 凭据失败", e);
        }
        return null;
    }

    @Override
    public URL getObjectUrl(String bucket, String objectKey) {
        ensureClientCreated();
        try {
            Response resp = client.getFileInfo(objectKey);
            if (resp.isSuccessful()) {
                return new URL(configuration.getUrlPrefix() == null ?
                        objectKey : configuration.getUrlPrefix() + objectKey);
            } else {
                throw new UpException(resp.message());
            }
        } catch (UpException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> getObjectList(String bucket, String path) {
        ensureClientCreated();

        // 预处理路径
        final String normalizedPath = path.endsWith("/") ? path : path + "/";
        List<String> res = new ArrayList<>();

        try (Response response = client.readDirIter(path, null)) {
            if (response.body() == null) {
                return res;
            }

            // 对于文件列表非常长的情况（上万个文件）可以显著减少内存抖动
            try (BufferedReader reader = new BufferedReader(response.body().charStream())) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split("\\s+");
                    if (parts.length > 0) {
                        String fileName = parts[0];
                        String fullPath = normalizedPath + fileName;

                        if (fullPath.startsWith("/")) {
                            fullPath = fullPath.substring(1);
                        }
                        res.add(fullPath);
                    }
                }
            }
        } catch (IOException | UpException e) {
            throw new RuntimeException("Failed to fetch object list from path: " + path, e);
        }

        return res;
    }

    @Override
    public Boolean deleteObject(String bucket, String objectKey) {
        ensureClientCreated();
        try {
            Response fileInfo = client.getFileInfo(objectKey);
            if (fileInfo.code() == 200) {
                return client.deleteFile(objectKey, null).isSuccessful();
            } else {
                throw new CikException("文件不存在！");
            }
        } catch (UpException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        ensureClientCreated();
        try (Response response = client.readFile(objectKey)) {
            if (response.isSuccessful() && response.body() != null) {
                ResponseBody body = response.body();
                InputStream inputStream = body.byteStream();
                // Java 8 兼容写法：将 InputStream 转为 ByteArrayInputStream
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[8192];
                int nRead;
                while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                return new ByteArrayInputStream(buffer.toByteArray());
            }
        } catch (Exception e) {
            log.error("获取文件流失败: {}", objectKey, e);
        }
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public void putObject(String bucket, String objectKey, InputStream input) {
        ensureClientCreated();
        try {
            Response fileInfo = client.getFileInfo(objectKey);
            if (fileInfo.code() == 200) {
                throw new RuntimeException("文件已存在: " + objectKey);
            }
            log.info("文件不存在，准备上传: {}", objectKey);
            try {
                Response response = client.writeFile(objectKey, input, null);
                if (response.isSuccessful()) {
                    log.info("上传成功");
                } else {
                    log.error("文件上传失败");
                    throw new RuntimeException("上传失败");
                }
            } catch (Exception ex) {

            }
        } catch (Exception e) {
            log.error("检查文件状态异常: {}", objectKey, e);
            throw new RuntimeException("服务异常");
        }
    }

    @Override
    public void createClient() {
        if (Objects.nonNull(this.client)) {
            return;
        }
        this.client = new RestManager(
                configuration.getBucket(),
                configuration.getAccessKey(),
                configuration.getSecretKey()
        );
        this.client.setApiDomain(RestManager.ED_AUTO);
    }

    /**
     * 内部保底初始化
     */
    private void ensureClientCreated() {
        if (this.client == null) {
            createClient();
        }
    }
}