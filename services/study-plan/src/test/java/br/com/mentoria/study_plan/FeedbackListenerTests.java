package br.com.mentoria.study_plan;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class FeedbackListenerTests {

    @Mock
    private StudyPlanCommands commands;

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "null", "{}"})
    void rejectsInvalidMessageWithoutWritingOrRequeuing(String body) {
        var listener = new FeedbackListener(new JsonMapper(), commands);

        assertThatThrownBy(() -> listener.receive(new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties())))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(commands);
    }
}
