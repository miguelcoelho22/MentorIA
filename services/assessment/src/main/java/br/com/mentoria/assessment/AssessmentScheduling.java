package br.com.mentoria.assessment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "assessment.outbox.enabled", havingValue = "true", matchIfMissing = true)
class AssessmentScheduling { }
