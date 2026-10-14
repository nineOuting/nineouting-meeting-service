package com.nineouting.backend.meeting.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class AppConfig {

    /** 회의 시각은 한국 시간으로 주고받으므로 시작·마무리 시각도 한국 시간으로 기록한다. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
