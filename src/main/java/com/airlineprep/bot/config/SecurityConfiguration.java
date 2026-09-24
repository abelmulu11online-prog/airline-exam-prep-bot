package com.airlineprep.bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean @Order(1)
    SecurityFilterChain adminSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/admin", "/admin/**", "/assets/**")
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/admin/login", "/assets/**").permitAll()
                    .anyRequest().hasRole("ADMIN"))
                .formLogin(login -> login.loginPage("/admin/login")
                    .loginProcessingUrl("/admin/login").defaultSuccessUrl("/admin", true)
                    .failureUrl("/admin/login?error"))
                .logout(logout -> logout.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?logout")
                    .invalidateHttpSession(true).deleteCookies("JSESSIONID"))
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .anyRequest().denyAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
                .build();
    }
}
