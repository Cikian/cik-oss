package cn.cikian.oss.autoconfigure;

import cn.cikian.oss.aop.OssAspectHandler;
import cn.cikian.oss.model.CikOssConfiguration;
import cn.cikian.oss.service.IOssService;
import cn.cikian.oss.service.OssServiceContext;
import cn.cikian.oss.service.impl.AliyunOssServiceImpl;
import cn.cikian.oss.service.impl.AmazonS3ServiceImpl;
import cn.cikian.oss.service.impl.MinIOServiceImpl;
import cn.cikian.oss.service.impl.UpyunServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

/**
 * OSS 自动配置类
 * 显式将配置对象传入各服务实现类的构造函数中。
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-05-15 01:36
 */
@Configuration
@EnableConfigurationProperties(CikOssConfiguration.class)
@ConditionalOnProperty(prefix = "oss", name = "enable", havingValue = "true")
@Import({OssAspectHandler.class}) // 在 Spring 环境下导入切面，支持自动初始化 client
public class OssAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(OssAutoConfiguration.class);

    /**
     * 注册阿里云 OSS 服务实现
     */
    @Bean
    @ConditionalOnProperty(prefix = "oss", name = "provider", havingValue = "ali")
    public IOssService aliyunOssService(CikOssConfiguration config) {
        log.info("注册阿里云 OSS 服务: Bucket: {}, 端点: {}", config.getBucket(), config.getEndpoint());
        return new AliyunOssServiceImpl(config);
    }

    /**
     * 注册亚马逊 S3 服务实现
     */
    @Bean
    @ConditionalOnProperty(prefix = "oss", name = "provider", havingValue = "aws")
    public IOssService amazonS3Service(CikOssConfiguration config) {
        log.info("注册亚马逊 S3 服务: Bucket: {}, 端点: {}", config.getBucket(), config.getEndpoint());
        return new AmazonS3ServiceImpl(config);
    }

    /**
     * 注册 MinIO 服务实现
     */
    @Bean
    @ConditionalOnProperty(prefix = "oss", name = "provider", havingValue = "minio")
    public IOssService minioService(CikOssConfiguration config) {
        log.info("注册MinIO 服务: Bucket: {}, 端点: {}", config.getBucket(), config.getEndpoint());
        return new MinIOServiceImpl(config);
    }

    /**
     * 注册 Upyun 服务实现
     */
    @Bean
    @ConditionalOnProperty(prefix = "oss", name = "provider", havingValue = "upyun")
    public IOssService UpyunService(CikOssConfiguration config) {
        log.info("注册又拍云服务: Bucket: {}", config.getBucket());
        return new UpyunServiceImpl(config);
    }

    /**
     * 注册 OSS 上下文
     * Spring 会自动收集上述已定义的三个 IOssService Bean 放入 ossServices 列表中
     */
    @Bean
    public OssServiceContext ossServiceContext(List<IOssService> ossServices, CikOssConfiguration config) {
        return new OssServiceContext(ossServices, config);
    }
}