package cn.cikian.oss.service.impl;

import cn.cikian.oss.model.CikOssConfiguration;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * OSS 切面处理器
 * 用于在执行存储操作前自动初始化客户端并校验配置
 *
 * @author sean
 * @version 1.2
 */
@Component
@Aspect
public class OssAspectHandler {

    @Autowired
    private OssServiceContext ossServiceContext;

    /**
     * 拦截 OssServiceContext 下的所有公开方法
     * 注意：execution 表达式中的包名需与你实际的项目结构一致
     */
    @Before("@annotation(cn.cikian.oss.annotations.OssCheck)")
    public void before() {
        // 1. 从 Context 中获取配置实例进行判断，不再使用静态引用
        CikOssConfiguration configuration = ossServiceContext.getConfiguration();

        if (configuration == null || !configuration.isEnable()) {
            throw new IllegalArgumentException("OSS 存储服务未启用，请检查配置项 'oss.enable'。");
        }

        // 2. 检查具体的服务实现类是否已成功根据 provider 匹配并注入
        if (this.ossServiceContext.getOssService() == null) {
            throw new IllegalArgumentException("未找到对应的 OSS 实现类，请检查 'oss.provider' 配置。");
        }

        // 3. 触发客户端初始化（如 Aliyun OSS 客户端或 MinioClient）
        this.ossServiceContext.createClient();
    }
}