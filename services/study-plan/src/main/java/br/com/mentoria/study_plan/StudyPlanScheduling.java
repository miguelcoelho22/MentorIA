package br.com.mentoria.study_plan;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "study-plan.projection.enabled", havingValue = "true", matchIfMissing = true)
class StudyPlanScheduling { }
