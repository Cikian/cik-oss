# CIK-OSS Tools

## 简介

OSS管理工具，支持阿里云OSS、AmazonS3、Minio、又拍云<br>
（更多厂商持续集成中...）

## 使用

```xml
<dependency>
    <groupId>cn.cikian</groupId>
    <artifactId>cik-oss</artifactId>
    <version>0.0.5</version>
</dependency>
```

latest：0.0.5

> Cikian Maven仓库地址：`http://mvnrep.cikian.cn/repository/public/`



### 1. Spring boot 项目

1. 在配置文件中开启服务

   在项目的 `application.yml` 或 `application.properties` 中添加 OSS 相关配置。只有 `oss.enable` 设置为 `true` 时，自动装配才会生效。

   配置信息：

   ```yaml
   oss:
     enable: true              		# 必须为 true 才会加载 Bean
     provider: AWS           			# 可选值：MINIO, ALIYUN, AWS, UPYUN
     endpoint: http://127.0.0.1:9000	# UPYUN可缺省
     access-key: your-access-key		# UPYUN为操作员
     secret-key: your-secret-key		# UPYUN为操作员密码
     role-session-name: cik-oss		# STS 角色会话名称
     role-arn: acs:ram::xxx:user/xxx	# STS 角色 ARN
     bucket: your-bucket-name			# 存储桶名称
     region: us-east-1         		# 地域，建议填写，部分厂商必须填写
     expire: 3600              		# 链接过期时间（秒）
     object-dir-drefix: photos/		# 对象存储目录前缀
     url-prefix: https://example.com	# url前缀，拼接实际访问地址，如果缺省，则使用bucket中的相对路径或默认厂商域名）
   ```

2. 在业务代码中注入使用

   ```java
   import cn.cikian.oss.service.OssServiceContext;
   
   @Service
   public class FileService {
   
       @Autowired
       private OssServiceContext ossContext; // 自动装配的 OSS 代理人服务
   
       /**
        * 上传示例
        */
       public void uploadFile(String fileName, InputStream inputStream) {
           // 直接使用，AOP 会自动处理对应厂商 client 的创建和初始化
           ossContext.putObject("my-bucket", objectKey, inputStream);
       }
   }
   ```



### 2. 非Spring Boot项目

1. 准备配置对象

   首先，需要手动创建一个 `CikOssConfiguration` 实例并填入参数

   ```java
   CikOssConfiguration config = new CikOssConfiguration();
   config.setEnable(true);
   config.setProvider(OssTypeEnum.MINIO); // 指定厂商
   config.setEndpoint("http://127.0.0.1:9000");
   config.setAccessKey("admin");
   config.setSecretKey("password");
   config.setBucket("my-bucket");
   config.setExpire(3600L);
   ```

2. 初始化具体的服务实现

   手动 `new` 出需要的实现类，并将配置对象传进去

   ```java
   // 以 MinIO 为例
   IOssService minioService = new MinIOServiceImpl(config);
   // 关键：非 Spring 环境没有 AOP 自动拦截，建议手动调用一次初始化客户端
   minioService.createClient();
   ```

3. 构建代理人（Context）

   为了保持代码逻辑的一致性，建议依然使用 `OssServiceContext` 来封装操作。

   ```java
   // 将实现类放入列表（模拟 Spring 的 List 注入）
   List<IOssService> services = Collections.singletonList(minioService);
   // 创建上下文
   OssServiceContext ossContext = new OssServiceContext(services, config);
   ```

4. 执行业务操作

   ```java
   public class FileService {
       // 通过上述步骤获取到context对象，或通过上述逻辑实现工具类、工厂类
   
       /**
        * 上传示例
        */
       public void uploadFile(String fileName, InputStream inputStream) {
           // 直接使用，AOP 会自动处理对应厂商 client 的创建和初始化
           ossContext.putObject("my-bucket", objectKey, inputStream);
       }
   }
   ```



## Context 方法

> 1. objectKey一般指对象在Bucket中的完整路径，例如：test/photos/、test/photos/cikian.jpg
>
> 2. 由于配置文件中已经定义了bucket，故以下所有方法中的`String bucket`参数可省略，未来可能会支持指定配置外的Bucket

1. 上传文件

   ```java
   void putObject(String objectKey, InputStream stream);
   void putObject(String bucket, String objectKey, InputStream stream);
   ```

2. 获取对象URL

   ```java
   URL getObjectUrl(String objectKey);
   URL getObjectUrl(String bucket, String objectKey);
   ```

3. 获取对象文件

   ```java
   InputStream getObject(String objectKey);
   InputStream getObject(String bucket, String objectKey);
   ```

4. 获取指定目录下的文件列表

   ```java
   List<String> getObjectList(String path);
   List<String> getObjectList(String bucket, String path);
   ```

   > 该方法返回对象的objectKey集合，例如，path为test/，则会返回path/下的所有文件名的集合：
   >
   > [test/a.jpg, test/b.pdf, test/c.png]

5. 删除对象

   ```java
   Boolean deleteObject(String bucket, String objectKey);
   Boolean deleteObject(String objectKey);
   ```

更多厂商、更多操作持续集成中，如需新增厂商或操作方法，可与我联系或者提交Issues