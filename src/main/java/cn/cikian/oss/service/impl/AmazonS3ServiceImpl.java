package cn.cikian.oss.service.impl;

import cn.cikian.oss.enmus.OssTypeEnum;
import cn.cikian.oss.model.CredentialsToken;
import cn.cikian.oss.model.CikOssConfiguration;
import cn.cikian.oss.service.IOssService;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * Amazon S3 存储实现
 * 支持 Spring Boot 自动装配及原生 Java 手动实例化
 * 兼容 Java 8
 */
public class AmazonS3ServiceImpl implements IOssService {

    private static final Logger log = LoggerFactory.getLogger(AmazonS3ServiceImpl.class);

    private AmazonS3 client;

    private final CikOssConfiguration configuration;

    /**
     * 构造函数注入配置对象
     */
    public AmazonS3ServiceImpl(CikOssConfiguration configuration) {
        log.info("AmazonS3 构造器注入配置");
        this.configuration = configuration;
        ensureClientCreated();
    }

    @Override
    public OssTypeEnum getOssType() {
        return OssTypeEnum.AWS;
    }

    @Override
    public CredentialsToken getCredentials() {
        try {
            AWSSecurityTokenService stsClient = AWSSecurityTokenServiceClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(
                            new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey())))
                    .withRegion(configuration.getRegion()).build();

            AssumeRoleRequest request = new AssumeRoleRequest()
                    .withRoleArn(configuration.getRoleArn())
                    .withRoleSessionName(configuration.getRoleSessionName())
                    .withDurationSeconds(Math.toIntExact(configuration.getExpire()));

            AssumeRoleResult result = stsClient.assumeRole(request);
            Credentials credentials = result.getCredentials();

            return new CredentialsToken(credentials.getAccessKeyId(), credentials.getSecretAccessKey(),
                    credentials.getSessionToken(), (credentials.getExpiration().getTime() - System.currentTimeMillis()) / 1000);
        } catch (Exception e) {
            log.error("获取Amazon STS 凭证失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public URL getObjectUrl(String bucket, String objectKey) {
        ensureClientCreated();
        return client.generatePresignedUrl(bucket, objectKey,
                new Date(System.currentTimeMillis() + configuration.getExpire() * 1000), HttpMethod.GET);
    }

    @Override
    public List<String> getObjectList(String bucket, String objectKey) {
        return Collections.emptyList();
    }

    @Override
    public Boolean deleteObject(String bucket, String objectKey) {
        ensureClientCreated();
        if (!client.doesObjectExist(bucket, objectKey)) {
            log.error("删除失败，对象[{}]不存在", objectKey);
            return false;
        }
        client.deleteObject(bucket, objectKey);
        log.info("删除对象[{}]成功", objectKey);
        return true;
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        ensureClientCreated();
        return client.getObject(bucket, objectKey).getObjectContent().getDelegateStream();
    }

    @Override
    public void putObject(String bucket, String objectKey, InputStream input) {
        ensureClientCreated();
        if (client.doesObjectExist(bucket, objectKey)) {
            log.error("文件已存在！");
            throw new RuntimeException("文件已存在: " + objectKey);
        }
        PutObjectResult objectResult = client.putObject(new PutObjectRequest(bucket, objectKey, input, new ObjectMetadata()));
        log.info("上传成功: {}", objectKey);
    }

    @Override
    public void createClient() {
        if (Objects.nonNull(this.client)) {
            return;
        }
        this.client = AmazonS3ClientBuilder.standard()
                .withCredentials(
                        new AWSStaticCredentialsProvider(
                                new BasicAWSCredentials(configuration.getAccessKey(), configuration.getSecretKey())))
                .withRegion(configuration.getRegion())
                .build();

        // 初始化完成后尝试配置 CORS
        configCORS();
    }

    /**
     * 配置跨域资源共享
     * 原本的 @PostConstruct 逻辑整合到此处，由 createClient 触发
     */
    private void configCORS() {
        // 只有在启用且当前选择的是 AWS 时才配置
        if (!configuration.isEnable() || OssTypeEnum.AWS != configuration.getProvider()) {
            return;
        }

        try {
            List<CORSRule.AllowedMethods> allowedMethods = new ArrayList<>();
            allowedMethods.add(CORSRule.AllowedMethods.GET);
            allowedMethods.add(CORSRule.AllowedMethods.POST);
            allowedMethods.add(CORSRule.AllowedMethods.DELETE);

            CORSRule rule = new CORSRule()
                    .withId("CORSAccessRule")
                    .withAllowedOrigins(Collections.singletonList("*"))
                    // 注意：AuthInterceptor.PARAM_TOKEN 如果是外部定义的常量，请确保该类在 classpath 中
                    .withAllowedHeaders(Collections.singletonList("token"))
                    .withAllowedMethods(allowedMethods);

            client.setBucketCrossOriginConfiguration(configuration.getBucket(),
                    new BucketCrossOriginConfiguration().withRules(Collections.singletonList(rule)));
        } catch (Exception e) {
            log.warn("S3 CORS 配置失败，请检查权限或存储桶配置", e);
        }
    }

    private void ensureClientCreated() {
        if (this.client == null) {
            createClient();
        }
    }
}