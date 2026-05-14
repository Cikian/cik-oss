package cn.cikian.oss.enmus;

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
}
