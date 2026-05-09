package com.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;

/**
 * MongoDB 配置类
 * 
 * 配置 MongoDB 模板，自定义类型映射器以移除 _class 字段
 */
@Configuration
public class MongoConfig {
    
    /**
     * 配置 MongoTemplate
     * 
     * 使用 MappingMongoConverter 并移除 _class 字段以减小存储空间
     * 
     * @param factory MongoDB 工厂
     * @param converter 类型转换器
     * @return MongoTemplate 实例
     */
    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory factory, MappingMongoConverter converter) {
        // 移除 _class 字段
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return new MongoTemplate(factory, converter);
    }
}