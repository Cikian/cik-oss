package cn.cikian.oss.enmus;

public enum OssTypeEnum {

    ALIYUN("ali"),

    AWS("aws"),

    MINIO("minio");

    private String type;

    OssTypeEnum(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
