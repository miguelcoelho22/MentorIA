package br.com.mentoria.saga_orchestrator.service;

import br.com.mentoria.saga_orchestrator.domain.Saga;
import br.com.mentoria.saga_orchestrator.domain.SagaStatus;
import br.com.mentoria.saga_orchestrator.messaging.*;
import br.com.mentoria.saga_orchestrator.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SagaService {

    private final SagaRepository sagaRepository;
    private final SagaProducer sagaProducer;
    private final AIProducer aiProducer;

    @Transactional
    public void iniciarSaga(RedacaoEnviadaEvent event) {

        Saga saga = new Saga();

        saga.setRedacaoId(event.redacaoId());
        saga.setAlunoId(event.alunoId());
        saga.setStatus(SagaStatus.IN_PROGRESS);
        saga.setCriadaEm(LocalDateTime.now());

        saga = sagaRepository.save(saga);

        ValidarCotaCommand command = new ValidarCotaCommand(
                saga.getId(),
                event.redacaoId(),
                event.alunoId()
        );

        sagaProducer.solicitarValidacaoCota(command);
    }

    @Transactional
    public void processarResultadoCota(CotaValidadaEvent event) {

        Saga saga = sagaRepository.findById(event.sagaId())
                .orElseThrow(() -> new RuntimeException(
                        "Saga não encontrada: " + event.sagaId()
                ));

        if (event.aprovada()) {

            ProcessarRedacaoCommand command = new ProcessarRedacaoCommand(
                    saga.getId(),
                    event.requestId(),
                    event.studentId()
            );

            aiProducer.solicitarProcessamento(command);

        } else {

            saga.setStatus(SagaStatus.FAILED);
            sagaRepository.save(saga);

            System.out.println("Cota rejeitada: " + event.motivo());
        }
    }
}