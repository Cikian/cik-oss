package cn.cikian.oss.model;

import cn.cikian.oss.enmus.OssTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author sean
 * @version 0.2
 * @date 2021/12/9
 */
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
