package br.com.mentoria.study_plan;

import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
class FeedbackListener {

    private static final Logger LOG = LoggerFactory.getLogger(FeedbackListener.class);

    private final ObjectMapper json;
    private final StudyPlanCommands commands;

    FeedbackListener(ObjectMapper json, StudyPlanCommands commands) {
        this.json = json;
        this.commands = commands;
    }

    @RabbitListener(queues = "${study-plan.rabbitmq.queue}")
    void receive(Message message) {
        FeedbackPublished event;
        try {
            event = json.readValue(message.getBody(), FeedbackPublished.class);
        } catch (JacksonException e) {
            throw new AmqpRejectAndDontRequeueException("JSON inválido para FeedbackPublished.", e);
        }
        validate(event);

        if (commands.recordFeedback(event)) {
            LOG.info("FeedbackPublished processado, eventId={}", event.eventId());
        } else {
            LOG.info("FeedbackPublished duplicado ignorado, eventId={}", event.eventId());
        }
    }

    private static void validate(FeedbackPublished event) {
        if (event == null || !validId(event.eventId()) || !validId(event.requestId()) || event.studentId() == null
                || event.competencies() == null || event.competencies().size() != 5) {
            throw new AmqpRejectAndDontRequeueException("FeedbackPublished exige eventId, requestId, studentId e cinco competências.");
        }
        var ids = new HashSet<Integer>();
        int total = 0;
        for (var competency : event.competencies()) {
            if (competency == null || competency.id() == null || competency.id() < 1 || competency.id() > 5
                    || !ids.add(competency.id()) || competency.score() == null
                    || competency.score() < 0 || competency.score() > 200) {
                throw new AmqpRejectAndDontRequeueException("Competências de FeedbackPublished inválidas.");
            }
            total += competency.score();
        }
        if (event.totalScore() != null && event.totalScore() != total) {
            throw new AmqpRejectAndDontRequeueException("totalScore diverge da soma das competências.");
        }
    }

    private static boolean validId(String id) {
        return id != null && !id.isBlank() && id.length() <= 128;
    }
}
