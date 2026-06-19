package cn.cikian.oss.model;

import cn.cikian.oss.enmus.OssTypeEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URL;

/**
 * OSS 配置类
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-05-15 01:36
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "oss")
public class CikOssConfiguration {

    /**
     * OSS 服务商类型
     */
    private OssTypeEnum provider;

    /**
     * 是否启用对象存储服务
     */
    private boolean enable;

    /**
     * 服务端点地址（需包含协议头，如 http://）
     */
    private String endpoint;

    /**
     * url前缀，用于拼接文件的实际访问地址，如果缺省，则只使用在bucket中的相对路径）
     */
    private String urlPrefix;

    /**
     * 访问密钥
     */
    private String accessKey;

    /**
     * 秘密密钥
     */
    private String secretKey;

    /**
     * 区域标识（如 cn-hangzhou）
     */
    private String region;

    /**
     * 凭证过期时间（秒）
     */
    private Long expire;

    /**
     * STS 角色会话名称
     */
    private String roleSessionName;

    /**
     * STS 角色 ARN
     */
    private String roleArn;

    /**
     * 存储桶名称
     */
    private String bucket;

    /**
     * 对象存储目录前缀
     */
    private String objectDirPrefix;

    /**
     * 兼容性构造函数：无参
     */
    public CikOssConfiguration() {
    }

    public boolean isEnable() {
        return enable;
    }

    /**
     * 安全地拼接并构建可访问的 URL 对象
     *
     * @param inputBucket 外部传入的 bucket，若为 null 或空则使用配置默认的 bucket
     * @param objectKey   对象的 key，支持带或不带前缀斜杠
     * @return 拼接好的 URL 对象，若参数不全或转换失败则返回 null
     */
    public URL buildObjectUrl(String inputBucket, String objectKey) {
        // 1. 基础校验
        if (this.endpoint == null || this.endpoint.trim().isEmpty()) {
            log.warn("未配置endpoint，不返回URL");
            return null;
        }
        if (objectKey == null || objectKey.trim().isEmpty()) {
            log.error("未传入objectKey，不返回URL");
            return null;
        }

        // 2. 决定最终使用的 Bucket 目标
        String targetBucket = (inputBucket != null && !inputBucket.trim().isEmpty()) ? inputBucket.trim() : this.bucket;
        if (targetBucket == null || targetBucket.trim().isEmpty()) {
            return null;
        }

        try {
            String cleanEndpoint = this.endpoint.trim();
            String cleanBucket = targetBucket.trim();
            String cleanObjectKey = objectKey.trim();

            // 3. 循环清洗 endpoint 尾部的斜杠
            while (cleanEndpoint.endsWith("/")) {
                cleanEndpoint = cleanEndpoint.substring(0, cleanEndpoint.length() - 1);
            }

            // 4. 循环清洗 bucket 首尾的斜杠
            while (cleanBucket.startsWith("/")) {
                cleanBucket = cleanBucket.substring(1);
            }
            while (cleanBucket.endsWith("/")) {
                cleanBucket = cleanBucket.substring(0, cleanBucket.length() - 1);
            }

            // 5. 循环清洗 objectKey 首尾的斜杠
            while (cleanObjectKey.startsWith("/")) {
                cleanObjectKey = cleanObjectKey.substring(1);
            }
            while (cleanObjectKey.endsWith("/")) {
                cleanObjectKey = cleanObjectKey.substring(0, cleanObjectKey.length() - 1);
            }

            // 6. 标准格式组装：endpoint/bucket/objectKey
            String finalUrlStr = cleanEndpoint + "/" + cleanBucket + "/" + cleanObjectKey;

            // 7. 完美兼容 JDK 8 的转换
            return new URI(finalUrlStr).toURL();
        } catch (Exception e) {
            log.error("URL拼接错误");
            return null;
        }
    }

    /**
     * 安全地拼接并构建可访问的 URL 对象
     *
     * @param objectKey   对象的 key，支持带或不带前缀斜杠
     * @return 拼接好的 URL 对象，若参数不全或转换失败则返回 null
     */
    public URL buildObjectUrl(String objectKey) {
        return this.buildObjectUrl(null, objectKey);
    }

    @Override
    public String toString() {
        return "---配置信息---\n" +
                "OSS服务商：" + this.provider.getType() + "\n" +
                "是否启用：" + isEnable() + "\n" +
                "服务端点地址：" + this.endpoint + "\n" +
                "访问账户：" + this.accessKey + "\n" +
                "访问密钥：" + this.secretKey + "\n" +
                "区域标识：" + this.region + "\n" +
                "凭证过期时间：" + this.expire + "\n" +
                "STS 角色会话名称：" + this.roleSessionName + "\n" +
                "STS 角色 ARN：" + this.roleArn + "\n" +
                "存储桶名称：" + this.bucket + "\n" +
                "对象存储目录前缀：" + this.objectDirPrefix + "\n";
    }
}
