package com.seatflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MyBatisConfigurationTests {

	@Autowired
	private SqlSessionFactory sqlSessionFactory;

	@Autowired
	private MybatisProperties mybatisProperties;

	@Test
	void myBatisConfigurationLoads() {
		assertThat(sqlSessionFactory.getConfiguration().isMapUnderscoreToCamelCase()).isTrue();
		assertThat(mybatisProperties.getMapperLocations()).containsExactly("classpath*:mappers/**/*.xml");
	}

}
