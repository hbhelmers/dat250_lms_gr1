package no.uib.dat250.LMS.events;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "lms.messaging.enabled", havingValue = "true")
public class MessagingConfiguration {
    public static final String EXCHANGE = "lms.events.v1";
    public static final String ACTIVITY_QUEUE = "lms.course-activity.v1";
    public static final String DEAD_EXCHANGE = "lms.events.dead";
    public static final String DEAD_QUEUE = "lms.course-activity.dead";

    @Bean Declarables learningEventTopology() {
        var events = new TopicExchange(EXCHANGE, true, false);
        var activity = QueueBuilder.durable(ACTIVITY_QUEUE).deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(DEAD_QUEUE).build();
        var dead = new DirectExchange(DEAD_EXCHANGE, true, false);
        var deadQueue = QueueBuilder.durable(DEAD_QUEUE).build();
        return new Declarables(events, activity, dead, deadQueue,
                BindingBuilder.bind(activity).to(events).with("quiz.submitted"),
                BindingBuilder.bind(activity).to(events).with("assignment.submitted"),
                BindingBuilder.bind(activity).to(events).with("grade.published"),
                BindingBuilder.bind(deadQueue).to(dead).with(DEAD_QUEUE));
    }
}
