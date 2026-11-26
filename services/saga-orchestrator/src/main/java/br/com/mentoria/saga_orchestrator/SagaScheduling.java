package br.com.mentoria.saga_orchestrator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "saga.watchdog.enabled", havingValue = "true", matchIfMissing = true)
class SagaScheduling { }
