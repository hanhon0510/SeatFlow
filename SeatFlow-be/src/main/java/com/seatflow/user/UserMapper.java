package com.seatflow.user;

import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface UserMapper {

	void insert(UserRecord user);

	void insertWithRole(UserRecord user);

	UserRecord findById(@Param("id") UUID id);

	UserRecord findByNormalizedEmail(@Param("normalizedEmail") String normalizedEmail);

	int updateStatus(@Param("id") UUID id, @Param("status") UserStatus status);

}
