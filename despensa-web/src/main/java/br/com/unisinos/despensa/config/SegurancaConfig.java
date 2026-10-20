package br.com.unisinos.despensa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

@Configuration
public class SegurancaConfig {

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requisicoes -> requisicoes
                        .requestMatchers("/login", "/usuarios/novo", "/saude",
                                "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("senha")
                        .defaultSuccessUrl("/")
                        .failureHandler(falhaNoLogin())
                        .permitAll())
                .logout(saida -> saida
                        .logoutSuccessUrl("/login?saiu")
                        .permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder();
    }

    private static AuthenticationFailureHandler falhaNoLogin() {
        return (requisicao, resposta, excecao) -> {
            String destino = excecao instanceof InternalAuthenticationServiceException
                    ? "/login?indisponivel"
                    : "/login?erro";
            resposta.sendRedirect(requisicao.getContextPath() + destino);
        };
    }
}
