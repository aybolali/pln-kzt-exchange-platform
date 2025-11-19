package pl.aybolali.plnkztexchangebot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import pl.aybolali.plnkztexchangebot.security.CustomUserDetailsService;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Security Filter Chain...");

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // H2 Console - for development only
                        .requestMatchers("/h2-console/**").permitAll()

                        // PUBLIC endpoints - no authentication required
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/check/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/exchange-requests").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/exchange-requests/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/deals/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/rates/current").permitAll()

                        // PUBLIC rating endpoints - anyone can view ratings
                        .requestMatchers(HttpMethod.GET, "/api/ratings/**").permitAll()

                        // PROTECTED endpoints - require authentication
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> {
                    log.info("HTTP Basic Authentication enabled");
                })
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.disable())
                )
                .authenticationProvider(authenticationProvider())
                .build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        log.info("Creating DaoAuthenticationProvider...");

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        log.info("Using NoOpPasswordEncoder for MVP (username = password)");
        // For MVP: username = password, no hashing
        return NoOpPasswordEncoder.getInstance();
    }
}