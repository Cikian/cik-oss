package cn.cikian.oss.autoconfigure;

import cn.cikian.oss.aop.OssAspectHandler;
import cn.cikian.oss.enmus.OssTypeEnum;
import cn.cikian.oss.model.CikOssConfiguration;
import cn.cikian.oss.service.IOssService;
import cn.cikian.oss.service.OssServiceContext;
import cn.cikian.oss.service.impl.AliyunOssServiceImpl;
import cn.cikian.oss.service.impl.AmazonS3ServiceImpl;
import cn.cikian.oss.service.impl.MinIOServiceImpl;
import cn.cikian.oss.service.impl.UpyunServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

/**
 * OSS 自动配置类
 * 显式将配置对象传入各服务实现类的构造函数中。
 * <p>
 * 关于厂商条件：这里不再用 {@code @ConditionalOnProperty} 去匹配 {@code ck.oss.provider} 的原始字符串，
 * 因为短别名（{@code ali}）与枚举名（{@code ALIYUN}）无法用单个 {@code havingValue} 同时命中，
 * 一旦不命中就会导致没有任何 {@link IOssService} 被注册、容器启动即失败。
 * 现在统一由绑定后的 {@link CikOssConfiguration#getProvider()} 决定实例化哪个实现类。
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-05-15 01:36
 */
@Configuration
@EnableConfigurationProperties(CikOssConfiguration.class)
@ConditionalOnProperty(prefix = "ck.oss", name = "enable", havingValue = "true")
@Import({OssAspectHandler.class}) // 在 Spring 环境下导入切面，支持自动初始化 client
public class OssAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(OssAutoConfiguration.class);

    /**
     * 注册 {@code ck.oss.provider} 的转换器
     * 让 {@code ali}/{@code ALIYUN} 两种写法都能正确绑定到 {@link OssTypeEnum}
     */
    @Bean
    @ConfigurationPropertiesBinding
    public OssTypeConverter ossTypeConverter() {
        return new OssTypeConverter();
    }

    /**
     * 按 {@code ck.oss.provider} 注册对应的存储服务实现
     */
    @Bean
    @ConditionalOnMissingBean(IOssService.class)
    public IOssService ossService(CikOssConfiguration config) {
        OssTypeEnum provider = config.getProvider();
        if (provider == null) {
            throw new IllegalArgumentException("未配置 OSS 提供商，请检查配置项 'ck.oss.provider'。可选值: "
                    + OssTypeEnum.supportedValues());
        }

        IOssService service;
        switch (provider) {
            case ALIYUN:
                service = new AliyunOssServiceImpl(config);
                break;
            case AWS:
                service = new AmazonS3ServiceImpl(config);
                break;
            case MINIO:
                service = new MinIOServiceImpl(config);
                break;
            case UPYUN:
                service = new UpyunServiceImpl(config);
                break;
            default:
                throw new IllegalArgumentException("暂不支持的 OSS 提供商: " + provider + "。可选值: "
                        + OssTypeEnum.supportedValues());
        }

        log.info("注册 OSS 服务实现: 提供商={}, Bucket={}, 端点={}", provider, config.getBucket(), config.getEndpoint());
        return service;
    }

    /**
     * 注册 OSS 上下文
     * Spring 会自动收集 {@link IOssService} Bean 放入 ossServices 列表中
     */
    @Bean
    @ConditionalOnMissingBean(OssServiceContext.class)
    public OssServiceContext ossServiceContext(List<IOssService> ossServices, CikOssConfiguration config) {
        return new OssServiceContext(ossServices, config);
    }
}
