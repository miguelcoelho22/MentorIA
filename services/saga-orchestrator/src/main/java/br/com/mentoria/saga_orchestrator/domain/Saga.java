package br.com.mentoria.saga_orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sagas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Saga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long redacaoId;

    private Long alunoId;

    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    private LocalDateTime criadaEm;
}