package cn.cikian.oss.service.impl;

import cn.cikian.oss.enmus.OssTypeEnum;
import cn.cikian.oss.exception.CikException;
import cn.cikian.oss.model.CikOssConfiguration;
import cn.cikian.oss.model.CredentialsToken;
import cn.cikian.oss.service.IOssService;
import com.upyun.RestManager;
import com.upyun.UpException;
import io.minio.credentials.AssumeRoleProvider;
import io.minio.credentials.Credentials;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        log.info("Upyun 构造器注入配置");
        this.configuration = configuration;
        this.ensureClientCreated();
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
                log.error("对象[{}]不存在", objectKey);
                throw new UpException(resp.message());
            }
        } catch (UpException | IOException e) {
            log.error("获取对象[{}]URL失败：{}", objectKey, e.getMessage());
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
                log.warn("对象列表[{}]为空", path);
                return res;
            }

            // 对于文件列表非常长的情况可以显著减少内存抖动
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
            log.error("获取对象列表[{}]异常：{}", path, e.getMessage());
            throw new RuntimeException("获取对象列表异常", e);
        }
        log.info("获取对象列表[{}]成功", path);
        return res;
    }

    @Override
    public Boolean deleteObject(String bucket, String objectKey) {
        ensureClientCreated();
        try {
            Response fileInfo = client.getFileInfo(objectKey);
            if (fileInfo.code() == 200) {
                boolean successful = client.deleteFile(objectKey, null).isSuccessful();
                if (successful) {
                    log.info("删除对象[{}]成功", objectKey);
                    return true;
                } else {
                    log.error("删除对象[{}]失败", objectKey);
                    return false;
                }
            } else {
                log.error("删除失败，对象[{}]不存在", objectKey);
                throw new CikException("对象不存在！");
            }
        } catch (UpException | IOException e) {
            log.error("删除对象[{}]失败: {}", objectKey, e.getMessage());
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
    public URL putObject(String bucket, String objectKey, InputStream input) {
        ensureClientCreated();

        try {
            // 1. 校验文件是否存在
            Response fileInfo = client.getFileInfo(objectKey);
            if (fileInfo != null && fileInfo.code() == 200) {
                throw new CikException("文件已存在: " + objectKey);
            }

            // 2. 执行文件上传
            Response response = client.writeFile(objectKey, input, null);
            if (response == null || !response.isSuccessful()) {
                log.error("文件上传失败，厂商返回状态异常");
                throw new CikException("上传失败");
            }
            log.info("上传成功: {}", objectKey);

            // 3. 完美的 URL 生成：直接甩锅给配置类的 buildObjectUrl 方法
            // 这样可以彻底避免双斜杠、少斜杠、漏写 bucket 以及特殊字符导致的任何非预期异常
            return configuration.buildObjectUrl(bucket, objectKey);

        } catch (RuntimeException e) {
            // 捕获我们主动抛出的业务异常（文件已存在、上传失败），记录日志并继续向上抛出
            log.error("业务处理异常: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // 捕获 client.getFileInfo 或 client.writeFile 触发的其它未知网络/IO系统异常
            log.error("上传文件系统状态异常: {}", objectKey, e);
            throw new CikException("服务异常", e);
        }
    }

    @Override
    public URL putObject(String bucket, String objectKey, byte[] bytes) {
        ensureClientCreated();

        try {
            // 1. 校验文件是否存在
            Response fileInfo = client.getFileInfo(objectKey);
            if (fileInfo != null && fileInfo.code() == 200) {
                throw new CikException("文件已存在: " + objectKey);
            }

            // 2. 执行文件上传
            Response response = client.writeFile(objectKey, bytes, null);
            if (response == null || !response.isSuccessful()) {
                log.error("文件上传失败，厂商返回状态异常");
                throw new CikException("上传失败");
            }
            log.info("上传成功: {}", objectKey);

            // 3. 完美的 URL 生成：直接甩锅给配置类的 buildObjectUrl 方法
            // 这样可以彻底避免双斜杠、少斜杠、漏写 bucket 以及特殊字符导致的任何非预期异常
            return configuration.buildObjectUrl(bucket, objectKey);

        } catch (RuntimeException e) {
            // 捕获我们主动抛出的业务异常（文件已存在、上传失败），记录日志并继续向上抛出
            log.error("业务处理异常: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // 捕获 client.getFileInfo 或 client.writeFile 触发的其它未知网络/IO系统异常
            log.error("上传文件系统状态异常: {}", objectKey, e);
            throw new CikException("服务异常", e);
        }
    }

    @Override
    public URL putObject(String bucket, String objectKey, File file) {
        ensureClientCreated();

        try {
            // 1. 校验文件是否存在
            Response fileInfo = client.getFileInfo(objectKey);
            if (fileInfo != null && fileInfo.code() == 200) {
                throw new CikException("文件已存在: " + objectKey);
            }

            // 2. 执行文件上传
            Response response = client.writeFile(objectKey, file, null);
            if (response == null || !response.isSuccessful()) {
                log.error("文件上传失败，厂商返回状态异常");
                throw new CikException("上传失败");
            }
            log.info("上传成功: {}", objectKey);

            // 3. 完美的 URL 生成：直接甩锅给配置类的 buildObjectUrl 方法
            // 这样可以彻底避免双斜杠、少斜杠、漏写 bucket 以及特殊字符导致的任何非预期异常
            return configuration.buildObjectUrl(bucket, objectKey);

        } catch (RuntimeException e) {
            // 捕获我们主动抛出的业务异常（文件已存在、上传失败），记录日志并继续向上抛出
            log.error("业务处理异常: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // 捕获 client.getFileInfo 或 client.writeFile 触发的其它未知网络/IO系统异常
            log.error("上传文件系统状态异常: {}", objectKey, e);
            throw new CikException("服务异常", e);
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

        String apiDomain = configuration.getApiDomain();

        if (StringUtils.isBlank(apiDomain)) {
            apiDomain = "AUTO";
        }

        switch (apiDomain) {
            case "TELECOM":
                this.client.setApiDomain(RestManager.ED_TELECOM);
                break;
            case "CNC":
                this.client.setApiDomain(RestManager.ED_CNC);
                break;
            case "CTT":
                this.client.setApiDomain(RestManager.ED_CTT);
                break;
            case "AUTO":
            default:
                this.client.setApiDomain(RestManager.ED_AUTO);
                break;
        }
        log.info("Upyun 创建Client成功，接入点：{}", apiDomain);
    }

    /**
     * 内部保底初始化
     */
    private void ensureClientCreated() {
        if (this.client == null) {
            log.warn("Upyun client为空，尝试创建Client");
            createClient();
        }
    }
}