package no.uib.dat250.LMS.content;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ContentConfiguration {
    @Bean
    Clock contentClock() { return Clock.systemUTC(); }
}
