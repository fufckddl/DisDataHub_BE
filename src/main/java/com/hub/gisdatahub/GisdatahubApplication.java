package com.hub.gisdatahub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.hub.gisdatahub.config.DotenvBootstrap;

@EnableScheduling
@SpringBootApplication
public class GisdatahubApplication {

	public static void main(String[] args) {
		// DevTools 재시작(RestartLauncher) 시에도 .env 가 반영되도록 JVM 시스템 프로퍼티에 먼저 올림
		DotenvBootstrap.applySystemProperties(GisdatahubApplication.class);
		SpringApplication.run(GisdatahubApplication.class, args);
	}

}
