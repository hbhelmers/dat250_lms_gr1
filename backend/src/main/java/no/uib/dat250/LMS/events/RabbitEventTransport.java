package no.uib.dat250.LMS.events;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lms.messaging.enabled", havingValue = "true")
public class RabbitEventTransport {
    private final RabbitTemplate rabbit;
    public RabbitEventTransport(RabbitTemplate rabbit) { this.rabbit = rabbit; }
    public void send(UUID id, String routingKey, String payload) throws Exception {
        var message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).setContentEncoding("UTF-8")
                .setMessageId(id.toString()).setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
        var confirmation = new CorrelationData(UUID.randomUUID().toString());
        rabbit.send(MessagingConfiguration.EXCHANGE, routingKey, message, confirmation);
        var result = confirmation.getFuture().get(5, TimeUnit.SECONDS);
        if (!result.isAck() || confirmation.getReturned() != null)
            throw new IllegalStateException("Event was not confirmed and routed.");
    }
}
