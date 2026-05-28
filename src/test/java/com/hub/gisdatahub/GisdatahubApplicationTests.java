package com.hub.gisdatahub;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class GisdatahubApplicationTests {

	@Autowired
	private Environment environment;

	@Test
	void contextLoads() {
	}

	@Test
	void testAwsPropertiesUseLocalDummyValues() {
		assertThat(environment.getProperty("aws.region")).isEqualTo("ap-northeast-2");
		assertThat(environment.getProperty("aws.s3.bucket")).isEqualTo("test-bucket");
		assertThat(environment.getProperty("aws.s3.access-key-id")).isEqualTo("test-access-key");
		assertThat(environment.getProperty("aws.s3.secret-access-key")).isEqualTo("test-secret-key");
	}

}
