package br.com.mentoria.saga_orchestrator.repository;

import br.com.mentoria.saga_orchestrator.domain.Saga;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaRepository extends JpaRepository<Saga, Long> {
}