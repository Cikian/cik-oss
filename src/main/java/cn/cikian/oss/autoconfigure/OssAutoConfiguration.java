package cn.cikian.oss.autoconfigure;

import cn.cikian.oss.model.CikOssConfiguration;
import cn.cikian.oss.service.IOssService;
import cn.cikian.oss.service.impl.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import java.util.List;

/**
 * OSS 自动配置类
 *
 * 修改点：
 * 1. 显式将配置对象传入各服务实现类的构造函数中。
 * 2. 移除原有的无参实例化，适配重构后的 Service。
 *
 * @author Cikian
 * @version 1.1
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-05-14
 */
@Configuration
@EnableConfigurationProperties(CikOssConfiguration.class)
@ConditionalOnProperty(prefix = "oss", name = "enable", havingValue = "true")
@Import({OssAspectHandler.class}) // 在 Spring 环境下导入切面，支持自动初始化 client
public class OssAutoConfiguration {

    /**
     * 注册阿里云 OSS 服务实现
     */
    @Bean
    @ConditionalOnProperty(prefix = "oss", name = "provider", havingValue = "ali")
    public IOssService aliyunOssService(CikOssConfiguration config) {
        System.out.println(">>>>>>>>>>>>Cikian: 注册阿里云 OSS 服务");
        System.out.println(config.toString());
        return new AliyunOssServiceImpl(config);
    }

    /**
     * 注册亚马逊 S3 服务实现
     */
    @Bean
    @ConditionalOnProperty(prefix = "oss", name = "provider", havingValue = "aws")
    public IOssService amazonS3Service(CikOssConfiguration config) {
        System.out.println(">>>>>>>>>>>>Cikian: 注册亚马逊 S3 服务");
        System.out.println(config.toString());
        return new AmazonS3ServiceImpl(config);
    }

    /**
     * 注册 MinIO 服务实现
     */
    @Bean
    @ConditionalOnProperty(prefix = "oss", name = "provider", havingValue = "minio")
    public IOssService minioService(CikOssConfiguration config) {
        System.out.println(">>>>>>>>>>>>Cikian: 注册MinIO 服务");
        System.out.println(config.toString());
        return new MinIOServiceImpl(config);
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