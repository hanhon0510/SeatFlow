package com.seatflow.health;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatabaseHealthMapper {

	String findSystemHealthStatus();

}
