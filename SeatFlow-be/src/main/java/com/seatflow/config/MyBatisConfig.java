package com.seatflow.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.seatflow", annotationClass = Mapper.class)
public class MyBatisConfig {
}
