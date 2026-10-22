package br.com.unisinos.despensa.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

@Configuration
public class SegurancaConfig {

    private static final List<String> PAGINAS_DO_MENU =
            List.of("/", "/cadastrar", "/consumir", "/alertas", "/relatorio", "/historico", "/produtos");

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requisicoes -> requisicoes
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/login", "/usuarios/novo", "/saude", "/css/**", "/js/**",
                                "/favicon.ico", "/favicon.svg", "/apple-touch-icon.png",
                                "/apple-touch-icon-precomposed.png").permitAll()
                        .anyRequest().authenticated())
                .requestCache(cache -> cache.requestCache(lembrarSoPaginasDoMenu()))
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

    private static RequestCache lembrarSoPaginasDoMenu() {
        PathPatternRequestMatcher.Builder caminhos = PathPatternRequestMatcher.withDefaults();
        List<RequestMatcher> paginas = PAGINAS_DO_MENU.stream()
                .map(pagina -> (RequestMatcher) caminhos.matcher(HttpMethod.GET, pagina))
                .toList();

        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(new OrRequestMatcher(paginas));
        return cache;
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
