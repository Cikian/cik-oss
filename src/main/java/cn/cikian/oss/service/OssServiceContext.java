package cn.cikian.oss.service;

import cn.cikian.oss.annotations.OssCheck;
import cn.cikian.oss.enmus.OssTypeEnum;
import cn.cikian.oss.model.CredentialsToken;
import cn.cikian.oss.model.CikOssConfiguration;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * OSS 服务上下文
 * 负责根据配置分发具体的存储策略（Aliyun/AWS/MinIO）
 * 支持 Spring 自动装配与 Native Java 手动实例化
 */
public class OssServiceContext {

    private IOssService ossService;

    private final CikOssConfiguration configuration;

    /**
     * 构造函数
     * 修改点：移除 @Autowired，改为通过构造器直接传入配置实例
     *
     * @param ossServices   所有实现的存储服务列表
     * @param configuration 配置对象实例
     */
    public OssServiceContext(List<IOssService> ossServices, CikOssConfiguration configuration) {
        this.configuration = configuration;

        // 校验配置是否启用
        if (configuration == null || !configuration.isEnable()) {
            return;
        }

        // 根据配置中的 provider 动态筛选服务
        this.ossService = ossServices.stream()
                .filter(service -> service.getOssType() == configuration.getProvider())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到匹配的 OSS 提供商。可选范围: " +
                        Arrays.toString(OssTypeEnum.values())));
    }

    /**
     * 获取当前激活的 OSS 服务实现
     */
    public IOssService getOssService() {
        return this.ossService;
    }

    /**
     * 获取配置实例（供切面 OssAspectHandler 使用）
     */
    public CikOssConfiguration getConfiguration() {
        return this.configuration;
    }

    public CredentialsToken getCredentials() {
        checkServiceReady();
        return this.ossService.getCredentials();
    }

    @OssCheck
    public URL getObjectUrl(String bucket, String objectKey) {
        objectKey = objectKey.replace("\\", "/");
        if (!hasText(bucket) || !hasText(objectKey)) {
            throw new IllegalArgumentException("Bucket name or object key must not be empty.");
        }
        checkServiceReady();
        return this.ossService.getObjectUrl(bucket, objectKey);
    }

    @OssCheck
    public URL getObjectUrl(String objectKey) {
        return getObjectUrl(configuration.getBucket(), objectKey);
    }

    @OssCheck
    public List<String> getObjectList(String bucket, String path) {
        if (!hasText(bucket) || !hasText(path)) {
            throw new IllegalArgumentException("Bucket name or object key must not be empty.");
        }
        checkServiceReady();
        return this.ossService.getObjectList(bucket, path);
    }

    @OssCheck
    public List<String> getObjectList(String path) {
        return getObjectList(configuration.getBucket(), path);
    }

    @OssCheck
    public Boolean deleteObject(String bucket, String objectKey) {
        objectKey = objectKey.replace("\\", "/");
        checkServiceReady();
        return this.ossService.deleteObject(bucket, objectKey);
    }

    @OssCheck
    public Boolean deleteObject(String objectKey) {
        return deleteObject(configuration.getBucket(), objectKey);
    }

    @OssCheck
    public InputStream getObject(String bucket, String objectKey) {
        objectKey = objectKey.replace("\\", "/");
        checkServiceReady();
        return this.ossService.getObject(bucket, objectKey);
    }

    @OssCheck
    public InputStream getObject(String objectKey) {
        return getObject(configuration.getBucket(), objectKey);
    }

    @OssCheck
    public URL putObject(String bucket, String objectKey, InputStream stream) {
        objectKey = objectKey.replace("\\", "/");
        checkServiceReady();
        return this.ossService.putObject(bucket, objectKey, stream);
    }

    @OssCheck
    public URL putObject(String bucket, String objectKey, byte[] bytes) {
        objectKey = objectKey.replace("\\", "/");
        checkServiceReady();
        return this.ossService.putObject(bucket, objectKey, bytes);
    }

    @OssCheck
    public URL putObject(String bucket, String objectKey, File file) {
        objectKey = objectKey.replace("\\", "/");
        checkServiceReady();
        return this.ossService.putObject(bucket, objectKey, file);
    }

    @OssCheck
    public URL putObject(String objectKey, InputStream stream) {
        return putObject(configuration.getBucket(), objectKey, stream);
    }

    @OssCheck
    public URL putObject(String objectKey, byte[] bytes) {
        return putObject(configuration.getBucket(), objectKey, bytes);
    }

    @OssCheck
    public URL putObject(String objectKey, File file) {
        return putObject(configuration.getBucket(), objectKey, file);
    }

    /**
     * 显式触发客户端创建
     */
    public void createClient() {
        if (this.ossService != null) {
            this.ossService.createClient();
        }
    }

    /**
     * 内部校验服务状态
     */
    private void checkServiceReady() {
        if (this.ossService == null) {
            throw new IllegalStateException("OSS 服务未初始化，请检查配置是否正确。");
        }
    }

    /**
     * 自定义简单实现 StringUtils.hasText 的逻辑，减少对 Spring 框架的强依赖
     */
    private boolean hasText(String str) {
        return str != null && !str.isEmpty() && str.trim().length() > 0;
    }
}