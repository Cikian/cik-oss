package cn.cikian.oss.service.impl;

import cn.cikian.oss.enmus.OssTypeEnum;
import cn.cikian.oss.model.CredentialsToken;
import cn.cikian.oss.model.CikOssConfiguration;
import cn.cikian.oss.service.IOssService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.sts.model.v20150401.AssumeRoleRequest;
import com.aliyuncs.sts.model.v20150401.AssumeRoleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.Objects;

/**
 * 阿里云 OSS 实现
 * 支持 Spring Boot 自动装配及原生 Java 手动实例化
 */
public class AliyunOssServiceImpl implements IOssService {

    private static final Logger log = LoggerFactory.getLogger(AliyunOssServiceImpl.class);

    private OSS ossClient;

    private final CikOssConfiguration configuration;

    /**
     * 构造函数：注入非静态配置对象
     * @param configuration 配置信息
     */
    public AliyunOssServiceImpl(CikOssConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public OssTypeEnum getOssType() {
        return OssTypeEnum.ALIYUN;
    }

    @Override
    public CredentialsToken getCredentials() {
        try {
            DefaultProfile profile = DefaultProfile.getProfile(
                    configuration.getRegion(),
                    configuration.getAccessKey(),
                    configuration.getSecretKey());
            IAcsClient client = new DefaultAcsClient(profile);

            AssumeRoleRequest request = new AssumeRoleRequest();
            request.setDurationSeconds(configuration.getExpire());
            request.setRoleArn(configuration.getRoleArn());
            request.setRoleSessionName(configuration.getRoleSessionName());

            AssumeRoleResponse.Credentials response = client.getAcsResponse(request).getCredentials();
            return new CredentialsToken(
                    response.getAccessKeyId(),
                    response.getAccessKeySecret(),
                    response.getSecurityToken(),
                    configuration.getExpire());

        } catch (ClientException e) {
            log.error("获取阿里云 STS 凭证失败", e);
        }
        return null;
    }

    @Override
    public URL getObjectUrl(String bucket, String objectKey) {
        ensureClientCreated();
        boolean isExist = ossClient.doesObjectExist(bucket, objectKey);
        if (!isExist) {
            throw new OSSException("对象不存在: " + objectKey);
        }

        return ossClient.generatePresignedUrl(bucket, objectKey,
                new Date(System.currentTimeMillis() + configuration.getExpire() * 1000));
    }

    @Override
    public Boolean deleteObject(String bucket, String objectKey) {
        ensureClientCreated();
        if (!ossClient.doesObjectExist(bucket, objectKey)) {
            return true;
        }
        ossClient.deleteObject(bucket, objectKey);
        return true;
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        ensureClientCreated();
        return ossClient.getObject(bucket, objectKey).getObjectContent();
    }

    @Override
    public void putObject(String bucket, String objectKey, InputStream input) {
        ensureClientCreated();
        if (ossClient.doesObjectExist(bucket, objectKey)) {
            throw new RuntimeException("文件名已存在: " + objectKey);
        }
        ossClient.putObject(new PutObjectRequest(bucket, objectKey, input, new ObjectMetadata()));
        log.info("文件上传成功: {}", objectKey);
    }

    @Override
    public void createClient() {
        if (Objects.nonNull(this.ossClient)) {
            return;
        }
        this.ossClient = new OSSClientBuilder()
                .build(configuration.getEndpoint(),
                        configuration.getAccessKey(),
                        configuration.getSecretKey());
    }

    /**
     * 内部保底检查：确保在非 Spring AOP 环境下调用时 Client 已初始化
     */
    private void ensureClientCreated() {
        if (this.ossClient == null) {
            createClient();
        }
    }
}