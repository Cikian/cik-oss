package cn.cikian.oss.service.impl;

import cn.cikian.oss.enmus.OssTypeEnum;
import cn.cikian.oss.model.CikOssConfiguration;
import cn.cikian.oss.model.CredentialsToken;
import cn.cikian.oss.service.IOssService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObjectSummary;
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

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 阿里云 OSS 实现
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-05-15 01:36
 */
public class AliyunOssServiceImpl implements IOssService {

    private static final Logger log = LoggerFactory.getLogger(AliyunOssServiceImpl.class);

    private OSS ossClient;

    private final CikOssConfiguration configuration;

    /**
     * 构造函数：注入非静态配置对象
     *
     * @param configuration 配置信息
     */
    public AliyunOssServiceImpl(CikOssConfiguration configuration) {
        log.info("ALI 构造器注入配置");
        this.configuration = configuration;
        ensureClientCreated();
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
            log.error("获取阿里云 STS 凭证失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public URL getObjectUrl(String bucket, String objectKey) {
        ensureClientCreated();
        boolean isExist = ossClient.doesObjectExist(bucket, objectKey);
        if (!isExist) {
            log.error("对象[{}]不存在", objectKey);
            throw new OSSException("对象不存在: " + objectKey);
        }

        return ossClient.generatePresignedUrl(bucket, objectKey,
                new Date(System.currentTimeMillis() + configuration.getExpire() * 1000));
    }

    @Override
    public List<String> getObjectList(String bucket, String objectKey) {
        List<String> res = new ArrayList<>();
        try {
            ListObjectsV2Result result = ossClient.listObjectsV2(bucket, objectKey);
            List<OSSObjectSummary> ossObjectSummaries = result.getObjectSummaries();
            for (OSSObjectSummary s : ossObjectSummaries) {
                res.add(s.getKey());
            }
        } catch (OSSException oe) {
            log.error("获取对象列表[{}]异常：{}", objectKey, oe.getMessage());
        }
        log.info("获取对象列表[{}]成功", objectKey);
        return res;
    }

    @Override
    public Boolean deleteObject(String bucket, String objectKey) {
        ensureClientCreated();
        if (!ossClient.doesObjectExist(bucket, objectKey)) {
            log.error("删除失败，对象[{}]不存在", objectKey);
            return false;
        }
        ossClient.deleteObject(bucket, objectKey);
        log.info("删除对象[{}]成功", objectKey);
        return true;
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        ensureClientCreated();
        return ossClient.getObject(bucket, objectKey).getObjectContent();
    }

    @Override
    public URL putObject(String bucket, String objectKey, InputStream input) {
        ensureClientCreated();

        // 1. 校验与上传
        if (ossClient.doesObjectExist(bucket, objectKey)) {
            throw new RuntimeException("文件已存在: " + objectKey);
        }

        try {
            ossClient.putObject(new PutObjectRequest(bucket, objectKey, input, new ObjectMetadata()));
            log.info("上传成功: {}", objectKey);
        } catch (Exception e) {
            throw new RuntimeException("服务异常", e);
        }

        // 2. 直接调用配置类里封装好的方法，把多斜杠、单斜杠、降级逻辑全部甩锅出去
        return configuration.buildObjectUrl(bucket, objectKey);
    }

    @Override
    public URL putObject(String bucket, String objectKey, byte[] bytes) {
        ensureClientCreated();
        log.error("阿里云暂未实现接收byte[]，请使用InputStream");
        return configuration.buildObjectUrl(bucket, null);
    }

    @Override
    public URL putObject(String bucket, String objectKey, File file) {
        ensureClientCreated();
        log.error("阿里云暂未实现接收File，请使用InputStream");
        return configuration.buildObjectUrl(bucket, null);
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
        log.info("ALI 创建Client");
    }

    /**
     * 内部保底检查：确保在非 Spring AOP 环境下调用时 Client 已初始化
     */
    private void ensureClientCreated() {
        if (this.ossClient == null) {
            log.warn("ALI client为空，尝试创建Client");
            createClient();
        }
    }
}