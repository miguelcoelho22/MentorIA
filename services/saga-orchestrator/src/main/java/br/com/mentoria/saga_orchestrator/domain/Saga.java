package br.com.mentoria.saga_orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

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

    private UUID redacaoId;

    private UUID alunoId;

    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    private LocalDateTime criadaEm;
}