package com.airlineprep.bot.payment;
import com.airlineprep.bot.telegram.TelegramAdminProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;
class TelegramAdminPropertiesTests {
 @Configuration(proxyBeanMethods=false) @EnableConfigurationProperties(TelegramAdminProperties.class) static class Config {}
 final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(Config.class);
 @Test void optionalAdminIdAndNumericConfiguration() {
  runner.run(c->{assertThat(c).hasNotFailed();assertThat(c.getBean(TelegramAdminProperties.class).id()).isNull();});
  runner.withPropertyValues("telegram.admin.id=123456").run(c->{assertThat(c).hasNotFailed();assertThat(c.getBean(TelegramAdminProperties.class).id()).isEqualTo(123456);});
 }
 @ParameterizedTest @ValueSource(strings={"0","-1","not-a-number","999999999999999999999999999"})
 void invalidConfiguredAdminIdRejected(String value) {runner.withPropertyValues("telegram.admin.id="+value).run(c->assertThat(c).hasFailed());}
}
