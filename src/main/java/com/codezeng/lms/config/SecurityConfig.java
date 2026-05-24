package com.codezeng.lms.config;

import com.codezeng.lms.service.DatabaseUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DatabaseUserDetailsService userDetailsService,
            @Value("${app.security.remember-me-key}") String rememberMeKey) throws Exception {
        http
                .userDetailsService(userDetailsService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/register", "/h2-console/**").permitAll()
                        .requestMatchers("/users/**").hasAuthority("USER_MANAGE")
                        .requestMatchers("/system/configs/**").hasAuthority("CONFIG_MANAGE")
                        .requestMatchers("/system/logs/**").hasAuthority("LOG_VIEW")
                        .requestMatchers("/recommendations/**").hasAuthority("RECOMMENDATION_VIEW")
                        .requestMatchers("/books/**").hasAuthority("BOOK_VIEW")
                        .requestMatchers("/readers/**").hasAuthority("READER_VIEW")
                        .requestMatchers("/borrow/**").hasAuthority("BORROW_MANAGE")
                        .requestMatchers("/reservations/**").hasAuthority("RESERVATION_MANAGE")
                        .requestMatchers("/notifications/**").hasAuthority("NOTIFICATION_VIEW")
                        .requestMatchers("/fines/**").hasAuthority("FINE_VIEW")
                        .anyRequest().authenticated()
                )
                .formLogin(login -> login
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .tokenValiditySeconds(7 * 24 * 60 * 60)
                        .userDetailsService(userDetailsService)
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
