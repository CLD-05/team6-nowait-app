package com.nowait;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Disabled // GitActions 빌드 시 이 테스트를 스킵하도록 설정
@SpringBootApplication
@EnableJpaAuditing
public class Team6NowaitApp1ApplicationTests {

	@Test
	void contextLoads() {
	}

}
