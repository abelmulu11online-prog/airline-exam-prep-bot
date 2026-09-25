package com.airlineprep.bot.common;
import java.time.Clock;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
public class ExamClockConfiguration {
 @Bean public Clock examClock() { return Clock.systemUTC(); }
}
