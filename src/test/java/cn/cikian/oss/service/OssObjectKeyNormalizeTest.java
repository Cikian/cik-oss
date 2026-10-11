package cn.cikian.oss.service;

import cn.cikian.oss.enmus.OssTypeEnum;
import cn.cikian.oss.model.CikOssConfiguration;
import cn.cikian.oss.model.CredentialsToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * objectKey 归一化回归测试
 * <p>
 * 复现线上问题：业务侧用 {@code dirPrefix + File.separator + "/biz/path"} 拼接时，
 * Windows 上会得到 {@code ck-saas\/shop/product/x.png}，库内只做反斜杠替换就会变成
 * {@code ck-saas//shop/product/x.png}，而 {@code //} 与 {@code /} 在对象存储里是两个不同的 key。
 *
 * @author Cikian
 * @version 1.0
 */
class OssObjectKeyNormalizeTest {

    private static final String BUCKET = "cikian-files";

    private CikOssConfiguration config(OssTypeEnum provider) {
        CikOssConfiguration configuration = new CikOssConfiguration();
        configuration.setEnable(true);
        configuration.setProvider(provider);
        configuration.setEndpoint("https://download.cikian.cn/");
        configuration.setBucket(BUCKET);
        return configuration;
    }

    @Test
    @DisplayName("dirPrefix + File.separator + \"/biz/path\" 不再产生双斜杠")
    void shouldCollapseDuplicateSlashProducedByCaller() {
        CikOssConfiguration configuration = config(OssTypeEnum.UPYUN);
        CapturingOssService service = new CapturingOssService();
        OssServiceContext ossContext = new OssServiceContext(Collections.singletonList(service), configuration);

        // 模拟 ck-shop CommonController：
        // putObject(dirPrefix + File.separator + bizPath + File.separator + fileName, ...)
        String rawKey = "ck-saas" + File.separator + "/shop/product" + File.separator + "BW-N033-removebg.png";
        ossContext.putObject(rawKey, new ByteArrayInputStream(new byte[0]));

        assertThat(service.lastKey).isEqualTo("ck-saas/shop/product/BW-N033-removebg.png");
    }

    @Test
    @DisplayName("其它写操作与读操作同样使用归一化后的 objectKey")
    void shouldNormalizeEveryKeyBasedOperation() {
        CikOssConfiguration configuration = config(OssTypeEnum.UPYUN);
        CapturingOssService service = new CapturingOssService();
        OssServiceContext ossContext = new OssServiceContext(Collections.singletonList(service), configuration);

        String messyKey = "ck-saas\\\\//shop//product/";

        ossContext.getObjectUrl(messyKey);
        assertThat(service.lastKey).isEqualTo("ck-saas/shop/product");

        ossContext.deleteObject(messyKey);
        assertThat(service.lastKey).isEqualTo("ck-saas/shop/product");

        ossContext.getObject(messyKey);
        assertThat(service.lastKey).isEqualTo("ck-saas/shop/product");
    }

    @Test
    @DisplayName("buildObjectUrl 生成的 URL 与归一化后的对象键一致")
    void shouldBuildUrlWithNormalizedObjectKey() {
        CikOssConfiguration upyun = config(OssTypeEnum.UPYUN);
        assertThat(upyun.buildObjectUrl("ck-saas//shop//product/BW-N033-removebg.png").toString())
                .isEqualTo("https://download.cikian.cn/ck-saas/shop/product/BW-N033-removebg.png");

        assertThat(upyun.buildObjectUrl("/ck-saas/shop/product/BW-N033-removebg.png").toString())
                .isEqualTo("https://download.cikian.cn/ck-saas/shop/product/BW-N033-removebg.png");

        CikOssConfiguration minio = config(OssTypeEnum.MINIO);
        assertThat(minio.buildObjectUrl("ck-saas\\/shop\\product\\BW-N033-removebg.png").toString())
                .isEqualTo("https://download.cikian.cn/cikian-files/ck-saas/shop/product/BW-N033-removebg.png");
    }

    /**
     * 只记录最近一次收到的 objectKey 的测试替身
     */
    private static class CapturingOssService implements IOssService {

        private String lastKey;

        @Override
        public OssTypeEnum getOssType() {
            return OssTypeEnum.UPYUN;
        }

        @Override
        public CredentialsToken getCredentials() {
            return null;
        }

        @Override
        public URL getObjectUrl(String bucket, String objectKey) {
            this.lastKey = objectKey;
            return null;
        }

        @Override
        public List<String> getObjectList(String bucket, String path) {
            this.lastKey = path;
            return Collections.emptyList();
        }

        @Override
        public Boolean deleteObject(String bucket, String objectKey) {
            this.lastKey = objectKey;
            return true;
        }

        @Override
        public InputStream getObject(String bucket, String objectKey) {
            this.lastKey = objectKey;
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public URL putObject(String bucket, String objectKey, InputStream input) {
            this.lastKey = objectKey;
            return null;
        }

        @Override
        public URL putObject(String bucket, String objectKey, byte[] bytes) {
            this.lastKey = objectKey;
            return null;
        }

        @Override
        public URL putObject(String bucket, String objectKey, File file) {
            this.lastKey = objectKey;
            return null;
        }

        @Override
        public void createClient() {
            // 测试替身，无需实现
        }
    }
}
