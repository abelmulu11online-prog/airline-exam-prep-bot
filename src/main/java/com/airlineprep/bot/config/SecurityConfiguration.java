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
        var loginEntryPoint = new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint("/admin/login");
        // Preserve the browser's HTTPS origin behind TLS termination without trusting forwarded headers.
        loginEntryPoint.setFavorRelativeUris(true);
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
                .headers(headers -> headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'none'; style-src 'self'; img-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"))
                    .referrerPolicy(policy -> policy.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .addFilterBefore(new LoginThrottleFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(loginEntryPoint).accessDeniedHandler((request,response,error) -> {
                    response.setStatus(403); response.setContentType("text/html;charset=UTF-8");
                    response.getWriter().write("<!doctype html><html lang=\"en\"><title>Action unavailable</title><h1>Action unavailable</h1><p>Your session may have expired. Sign in again, reload the form and try again.</p><a href=\"/admin/login\">Sign in</a></html>");
                }))
                .build();
    }

    @Bean @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/telegram/webhook").permitAll()
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/telegram/webhook", "POST")))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
                .build();
    }
}
