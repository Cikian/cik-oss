package cn.cikian.oss.autoconfigure;

import cn.cikian.oss.enmus.OssTypeEnum;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

/**
 * OSS 厂商配置转换器
 * <p>
 * Spring Boot 默认只会把 {@code ck.oss.provider} 按枚举名（{@code ALIYUN}、{@code AWS}、{@code MINIO}、{@code UPYUN}）绑定，
 * 而本组件历史上一直使用短别名（{@code ali}、{@code aws}、{@code minio}、{@code upyun}）。
 * 该转换器让两种写法都能被正确绑定，避免出现
 * {@code No enum constant cn.cikian.oss.enmus.OssTypeEnum.ali} 这类启动失败。
 * <p>
 * 由 {@link OssAutoConfiguration} 以 Bean 的方式注册，不依赖宿主项目的组件扫描。
 *
 * @author Cikian
 * @version 1.0
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 */
@ConfigurationPropertiesBinding
public class OssTypeConverter implements Converter<String, OssTypeEnum> {

    @Override
    public OssTypeEnum convert(String source) {
        OssTypeEnum result = OssTypeEnum.fromValue(source);
        if (result == null) {
            throw new IllegalArgumentException("无法识别的 OSS 提供商: '" + source
                    + "'，请检查配置项 'ck.oss.provider'。可选值: " + OssTypeEnum.supportedValues());
        }
        return result;
    }
}
