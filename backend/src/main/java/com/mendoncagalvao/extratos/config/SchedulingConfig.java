package com.mendoncagalvao.extratos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita {@code @Scheduled} (usado pelo polling de e-mail, §13). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
