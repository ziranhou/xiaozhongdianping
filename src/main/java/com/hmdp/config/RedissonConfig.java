package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){    // 一个工厂方法，返回RedissonClient对象
        // 声明一个配置类
        Config config = new Config();
        // 添加redis地址，这里添加的是单点地址，也可以使用config.useClusterServers()添加集群地址
        config.useSingleServer().setAddress("redis://192.168.15.129:6379");
        // 创建客户端
        return Redisson.create(config);
    }
}