package cn.cikian.oss.service.impl;

import cn.cikian.oss.enmus.OssTypeEnum;
import cn.cikian.oss.model.CredentialsToken;
import cn.cikian.oss.model.CikOssConfiguration;
import cn.cikian.oss.service.IOssService;
import io.minio.*;
import io.minio.credentials.AssumeRoleProvider;
import io.minio.credentials.Credentials;
import io.minio.errors.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * MinIO 存储实现
 * 支持 Spring Boot 自动装配及原生 Java 手动实例化
 */
public class MinIOServiceImpl implements IOssService {

    private static final Logger log = LoggerFactory.getLogger(MinIOServiceImpl.class);

    private MinioClient client;

    private final CikOssConfiguration configuration;

    /**
     * 构造函数注入配置
     * @param configuration 非静态配置对象
     */
    public MinIOServiceImpl(CikOssConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public OssTypeEnum getOssType() {
        return OssTypeEnum.MINIO;
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
            return new URL(
                    client.getPresignedObjectUrl(
                            GetPresignedObjectUrlArgs.builder()
                                    .method(Method.GET)
                                    .bucket(bucket)
                                    .object(objectKey)
                                    .expiry(Math.toIntExact(configuration.getExpire()))
                                    .build()));
        } catch (Exception e) {
            log.error("获取文件预签名地址失败: {}", objectKey, e);
            throw new RuntimeException("文件不存在或获取地址失败");
        }
    }

    @Override
    public Boolean deleteObject(String bucket, String objectKey) {
        ensureClientCreated();
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return true;
        } catch (Exception e) {
            log.error("删除文件失败: {}", objectKey, e);
            return false;
        }
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        ensureClientCreated();
        try (GetObjectResponse object = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {

            // Java 8 兼容写法：将 InputStream 转为 ByteArrayInputStream
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = object.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return new ByteArrayInputStream(buffer.toByteArray());

        } catch (Exception e) {
            log.error("获取文件流失败: {}", objectKey, e);
        }
        // Java 8 兼容写法：返回空流
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public void putObject(String bucket, String objectKey, InputStream input) {
        ensureClientCreated();
        try {
            // 检查是否存在
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            throw new RuntimeException("文件名已存在: " + objectKey);
        } catch (ErrorResponseException e) {
            // 只有 404 错误才进行上传
            log.info("文件不存在，准备上传: {}", objectKey);
            try {
                ObjectWriteResponse response = client.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .stream(input, -1, 10485760L) // -1 代表未知大小分片上传
                                .build());
                log.info("上传成功，ETag: {}", response.etag());
            } catch (Exception ex) {
                log.error("文件上传异常: {}", objectKey, ex);
                throw new RuntimeException("上传失败");
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
        this.client = MinioClient.builder()
                .endpoint(configuration.getEndpoint())
                .credentials(configuration.getAccessKey(), configuration.getSecretKey())
                .region(configuration.getRegion())
                .build();
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