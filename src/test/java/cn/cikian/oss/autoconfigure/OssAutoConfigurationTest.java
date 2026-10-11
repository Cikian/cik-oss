package cn.cikian.oss.autoconfigure;

import cn.cikian.oss.enmus.OssTypeEnum;
import cn.cikian.oss.model.CikOssConfiguration;
import cn.cikian.oss.service.IOssService;
import cn.cikian.oss.service.OssServiceContext;
import cn.cikian.oss.service.impl.AliyunOssServiceImpl;
import cn.cikian.oss.service.impl.AmazonS3ServiceImpl;
import cn.cikian.oss.service.impl.MinIOServiceImpl;
import cn.cikian.oss.service.impl.UpyunServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OSS 自动配置回归测试
 * <p>
 * 通过 Spring Boot 的自动配置发现机制（META-INF/spring/...AutoConfiguration.imports）加载
 * {@link OssAutoConfiguration}，验证在宿主工程中 {@link OssServiceContext} 能够被直接注入，
 * 且 {@code ck.oss.provider} 的短别名与枚举名两种写法都能正常工作。
 *
 * @author Cikian
 * @version 1.0
 */
class OssAutoConfigurationTest {

    /**
     * 不显式指定配置类，走 spring.factories / AutoConfiguration.imports 的自动发现
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssAutoConfiguration.class));

    @Test
    @DisplayName("ck.oss.enable=true 时 OssServiceContext 可被注入（短别名 ali）")
    void shouldRegisterContextWithShortAlias() {
        runner.withPropertyValues("ck.oss.enable=true", "ck.oss.provider=ali")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OssServiceContext.class);
                    assertThat(context).hasSingleBean(IOssService.class);
                    assertThat(context.getBean(OssServiceContext.class).getOssService())
                            .isInstanceOf(AliyunOssServiceImpl.class);
                });
    }

    @Test
    @DisplayName("ck.oss.provider 支持枚举名 ALIYUN 与小写 aliyun")
    void shouldSupportEnumNameAndLowerCase() {
        runner.withPropertyValues("ck.oss.enable=true", "ck.oss.provider=ALIYUN")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CikOssConfiguration.class).getProvider()).isEqualTo(OssTypeEnum.ALIYUN);
                });

        runner.withPropertyValues("ck.oss.enable=true", "ck.oss.provider=aliyun")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CikOssConfiguration.class).getProvider()).isEqualTo(OssTypeEnum.ALIYUN);
                });
    }

    @Test
    @DisplayName("其余厂商别名都能匹配到对应实现")
    void shouldMatchOtherProviders() {
        runner.withPropertyValues("ck.oss.enable=true", "ck.oss.provider=aws")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(IOssService.class)).isInstanceOf(AmazonS3ServiceImpl.class);
                });

        runner.withPropertyValues("ck.oss.enable=true", "ck.oss.provider=minio")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(IOssService.class)).isInstanceOf(MinIOServiceImpl.class);
                });

        runner.withPropertyValues("ck.oss.enable=true", "ck.oss.provider=upyun")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(IOssService.class)).isInstanceOf(UpyunServiceImpl.class);
                });
    }

    @Test
    @DisplayName("ck.oss.enable=false 时不注册任何 OSS Bean")
    void shouldNotRegisterWhenDisabled() {
        runner.withPropertyValues("ck.oss.enable=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(OssServiceContext.class);
                    assertThat(context).doesNotHaveBean(IOssService.class);
                });
    }

    @Test
    @DisplayName("provider 缺失或非法时启动即失败，并给出可选值提示")
    void shouldFailFastOnMissingOrInvalidProvider() {
        runner.withPropertyValues("ck.oss.enable=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("ck.oss.provider");
                });

        runner.withPropertyValues("ck.oss.enable=true", "ck.oss.provider=not-exist")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("无法识别的 OSS 提供商");
                });
    }

    @Test
    @DisplayName("宿主工程自定义 IOssService 时不会被覆盖（provider 与自定义实现一致）")
    void shouldBackOffWhenHostDefinesService() {
        runner.withUserConfiguration(CustomServiceConfiguration.class)
                .withPropertyValues("ck.oss.enable=true", "ck.oss.provider=minio")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(IOssService.class)).hasSize(1);
                    assertThat(context.getBean(OssServiceContext.class).getOssService())
                            .isSameAs(context.getBean("customOssService"));
                });
    }

    @Test
    @DisplayName("自定义实现与 ck.oss.provider 不匹配时快速失败并提示可选值")
    void shouldFailWhenCustomServiceDoesNotMatchProvider() {
        runner.withUserConfiguration(CustomServiceConfiguration.class)
                .withPropertyValues("ck.oss.enable=true", "ck.oss.provider=ali")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("ck.oss.provider");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomServiceConfiguration {

        @Bean
        IOssService customOssService() {
            return new IOssService() {
                @Override
                public OssTypeEnum getOssType() {
                    return OssTypeEnum.MINIO;
                }

                @Override
                public cn.cikian.oss.model.CredentialsToken getCredentials() {
                    return null;
                }

                @Override
                public java.net.URL getObjectUrl(String bucket, String objectKey) {
                    return null;
                }

                @Override
                public java.util.List<String> getObjectList(String bucket, String path) {
                    return null;
                }

                @Override
                public Boolean deleteObject(String bucket, String objectKey) {
                    return null;
                }

                @Override
                public java.io.InputStream getObject(String bucket, String objectKey) {
                    return null;
                }

                @Override
                public java.net.URL putObject(String bucket, String objectKey, java.io.InputStream input) {
                    return null;
                }

                @Override
                public java.net.URL putObject(String bucket, String objectKey, byte[] bytes) {
                    return null;
                }

                @Override
                public java.net.URL putObject(String bucket, String objectKey, java.io.File file) {
                    return null;
                }

                @Override
                public void createClient() {
                    // 测试替身，无需实现
                }
            };
        }
    }
}
