package com.codezeng.lms.config;

import com.codezeng.lms.domain.enums.UserRole;
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
                        .requestMatchers("/users/**", "/system/**").hasRole(UserRole.SUPER_ADMIN.name())
                        .requestMatchers("/api/**", "/books/**", "/readers/**", "/borrow/**", "/reservations/**", "/fines/**")
                        .hasAnyRole(UserRole.SUPER_ADMIN.name(), UserRole.LIBRARIAN.name())
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
