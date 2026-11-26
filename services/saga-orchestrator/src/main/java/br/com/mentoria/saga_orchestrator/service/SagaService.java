package br.com.mentoria.saga_orchestrator.service;

import br.com.mentoria.saga_orchestrator.domain.Saga;
import br.com.mentoria.saga_orchestrator.domain.SagaStatus;
import br.com.mentoria.saga_orchestrator.messaging.*;
import br.com.mentoria.saga_orchestrator.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SagaService {

    private final SagaRepository sagaRepository;
    private final SagaProducer sagaProducer;
    private final AIProducer aiProducer;
    private final FeedbackProducer feedbackProducer;

    @Transactional
    public void iniciarSaga(RedacaoEnviadaEvent event) {

        Saga saga = new Saga();

        saga.setRequestId(UUID.randomUUID());
        saga.setRedacaoId(event.redacaoId());
        saga.setAlunoId(event.alunoId());
        saga.setTheme(event.theme());
        saga.setText(event.text());
        saga.setStatus(SagaStatus.IN_PROGRESS);
        saga.setCriadaEm(LocalDateTime.now());

        saga = sagaRepository.save(saga);

        ValidarCotaCommand command = new ValidarCotaCommand(
                saga.getId(),
                saga.getRequestId(),
                saga.getAlunoId()
        );

        sagaProducer.solicitarValidacaoCota(command);
    }

    @Transactional
    public void processarResultadoCota(CotaValidadaEvent event) {

        Saga saga = buscarSaga(event.sagaId());

        validarRequestId(saga, event.requestId());

        if (event.aprovada()) {

            ProcessarRedacaoCommand command = new ProcessarRedacaoCommand(
                    saga.getId(),
                    saga.getRequestId(),
                    saga.getAlunoId(),
                    saga.getRedacaoId(),
                    saga.getTheme(),
                    saga.getText()
            );

            aiProducer.solicitarProcessamento(command);

        } else {

            saga.setStatus(SagaStatus.FAILED);
            sagaRepository.save(saga);

            System.out.println("Cota rejeitada: " + event.motivo());
        }
    }

    @Transactional
    public void processarResultadoAnalise(RedacaoAnalisadaEvent event) {

        Saga saga = buscarSaga(event.sagaId());

        validarRequestId(saga, event.requestId());

        GerarFeedbackCommand command = new GerarFeedbackCommand(
                saga.getId(),
                saga.getRequestId(),
                saga.getAlunoId(),
                saga.getRedacaoId(),
                event.competencies()
        );

        feedbackProducer.solicitarGeracaoFeedback(command);

        System.out.println("Solicitação de geração de feedback enviada.");
    }

    @Transactional
    public void processarFeedbackPublicado(FeedbackPublishedEvent event) {

        Saga saga = buscarSaga(event.sagaId());

        validarRequestId(saga, event.requestId());

        saga.setStatus(SagaStatus.COMPLETED);

        sagaRepository.save(saga);

        System.out.println(
                "Saga " + saga.getId()
                        + " concluída. Nota total: " + event.totalScore()
        );
    }

    private Saga buscarSaga(Long sagaId) {

        return sagaRepository.findById(sagaId)
                .orElseThrow(() -> new RuntimeException(
                        "Saga não encontrada: " + sagaId
                ));
    }

    private void validarRequestId(Saga saga, UUID requestId) {

        if (!saga.getRequestId().equals(requestId)) {
            throw new RuntimeException(
                    "RequestId não corresponde à Saga: " + saga.getId()
            );
        }
    }
}