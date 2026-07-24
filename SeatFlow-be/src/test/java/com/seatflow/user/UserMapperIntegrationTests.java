package com.seatflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.support.PostgresTestContainerSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class UserMapperIntegrationTests extends PostgresTestContainerSupport {

	@Autowired
	private UserMapper userMapper;

	@Test
	void insertsAndRetrievesUserWithDefaults() {
		UserRecord user = newUser("insert");

		userMapper.insert(user);

		UserRecord found = userMapper.findById(user.id());
		assertThat(found).isNotNull();
		assertThat(found.id()).isEqualTo(user.id());
		assertThat(found.email()).isEqualTo(user.email());
		assertThat(found.passwordHash()).isEqualTo(user.passwordHash());
		assertThat(found.role()).isEqualTo(UserRole.USER);
		assertThat(found.status()).isEqualTo(UserStatus.ACTIVE);
		assertThat(found.createdAt()).isNotNull();
		assertThat(found.updatedAt()).isNotNull();
	}

	@Test
	void findsUserByNormalizedEmail() {
		String email = "Find-%s@Example.com".formatted(UUID.randomUUID());
		UserRecord user = UserRecord.forInsert(UUID.randomUUID(), email, "{bcrypt}find-email");
		userMapper.insert(user);

		UserRecord found = userMapper.findByNormalizedEmail(user.email().toLowerCase(Locale.ROOT));

		assertThat(found).isNotNull();
		assertThat(found.id()).isEqualTo(user.id());
		assertThat(found.email()).isEqualTo(user.email());
	}

	@Test
	void duplicateNormalizedEmailFails() {
		String email = "Duplicate-%s@Example.com".formatted(UUID.randomUUID());
		UserRecord first = UserRecord.forInsert(UUID.randomUUID(), email, "{bcrypt}first");
		UserRecord second = UserRecord.forInsert(UUID.randomUUID(), email.toLowerCase(Locale.ROOT), "{bcrypt}second");

		userMapper.insert(first);

		assertThatThrownBy(() -> userMapper.insert(second))
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	void updatesStatus() {
		UserRecord user = newUser("status");
		userMapper.insert(user);

		int updatedRows = userMapper.updateStatus(user.id(), UserStatus.DISABLED);
		UserRecord found = userMapper.findById(user.id());

		assertThat(updatedRows).isEqualTo(1);
		assertThat(found.status()).isEqualTo(UserStatus.DISABLED);
	}

	@Test
	void mapperResultMappingBuildsUserDomainModel() {
		UserRecord user = newUser("mapping");
		userMapper.insert(user);

		User domainUser = userMapper.findById(user.id()).toUser();

		assertThat(domainUser.id()).isEqualTo(user.id());
		assertThat(domainUser.email()).isEqualTo(user.email());
		assertThat(domainUser.passwordHash()).isEqualTo(user.passwordHash());
		assertThat(domainUser.role()).isEqualTo(UserRole.USER);
		assertThat(domainUser.status()).isEqualTo(UserStatus.ACTIVE);
		assertThat(domainUser.createdAt()).isNotNull();
		assertThat(domainUser.updatedAt()).isNotNull();
	}

	private static UserRecord newUser(String label) {
		return UserRecord.forInsert(
				UUID.randomUUID(),
				"%s-%s@example.com".formatted(label, UUID.randomUUID()),
				"{bcrypt}%s".formatted(UUID.randomUUID()));
	}

}
