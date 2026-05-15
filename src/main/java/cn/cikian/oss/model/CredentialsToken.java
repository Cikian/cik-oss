package cn.cikian.oss.model;

/**
 * OSS 临时访问凭证
 *
 * @author Cikian
 * @version 1.0
 * @implNote
 * @see <a href="https://www.cikian.cn">https://www.cikian.cn</a>
 * @since 2026-05-15 01:36
 */
public class CredentialsToken {

    private static final int DELAY = 300;

    private String accessKeyId;

    private String accessKeySecret;

    private Long expire;

    private String securityToken;

    public CredentialsToken(String accessKeyId, String accessKeySecret, String securityToken, Long expire) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.securityToken = securityToken;
        this.expire = expire - DELAY;
    }

    public CredentialsToken() {
    }

    @Override
    public String toString() {
        return "CredentialsToken{" +
                "accessKeyId='" + accessKeyId + '\'' +
                ", accessKeySecret='" + accessKeySecret + '\'' +
                ", expire=" + expire +
                ", securityToken='" + securityToken + '\'' +
                '}';
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public CredentialsToken setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
        return this;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public CredentialsToken setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
        return this;
    }

    public Long getExpire() {
        return expire;
    }

    public CredentialsToken setExpire(Long expire) {
        this.expire = expire;
        return this;
    }

    public String getSecurityToken() {
        return securityToken;
    }

    public CredentialsToken setSecurityToken(String securityToken) {
        this.securityToken = securityToken;
        return this;
    }
}