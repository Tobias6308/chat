package com.chat.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文辅助类
 * 
 * 用于在非 Spring 管理的类中获取 Bean
 * (如 Netty Handler 中获取 Service)
 */
@Component
public class ApplicationContextHelper implements ApplicationContextAware {
    
    /**
     * Spring 应用上下文
     */
    private static ApplicationContext applicationContext;
    
    /**
     * 实现 ApplicationContextAware 接口
     * 在 Bean 初始化时注入 ApplicationContext
     * 
     * @param context 应用上下文
     * @throws BeansException 如果获取失败
     */
    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }
    
    /**
     * 根据类型获取 Bean
     * 
     * @param clazz Bean 类型
     * @param <T> 泛型类型
     * @return Bean 实例
     */
    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }
    
    /**
     * 根据名称获取 Bean
     * 
     * @param name Bean 名称
     * @param <T> 泛型类型
     * @return Bean 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        return (T) applicationContext.getBean(name);
    }
}