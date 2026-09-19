package cn.cikian.oss.enmus;

/**
 * OSS 厂商定义
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-05-15 01:36
 */
public enum OssTypeEnum {

    ALIYUN("ali"),

    AWS("aws"),

    MINIO("minio"),

    UPYUN("upyun"),
    ;

    private String type;

    OssTypeEnum(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    /**
     * 按「枚举名」或「厂商别名」解析厂商，忽略大小写与首尾空白。
     * <p>
     * 支持的值：{@code ALIYUN/aliyun}、{@code ali}、{@code AWS/aws}、{@code MINIO/minio}、{@code UPYUN/upyun}。
     *
     * @param value 配置值，如 {@code ck.oss.provider=ALIYUN} 或 {@code ck.oss.provider=ali}
     * @return 匹配的厂商枚举；无法匹配时返回 {@code null}
     */
    public static OssTypeEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (OssTypeEnum item : values()) {
            if (item.name().equalsIgnoreCase(trimmed) || item.type.equalsIgnoreCase(trimmed)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 所有可用的配置值，用于异常提示
     */
    public static String supportedValues() {
        StringBuilder sb = new StringBuilder();
        for (OssTypeEnum item : values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(item.name()).append('/').append(item.type);
        }
        return sb.toString();
    }
}
