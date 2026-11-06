package br.com.unisinos.despensa.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.RequestMatcherDelegatingAccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;
import java.util.List;

@Configuration
public class SegurancaConfig {

    private static final List<String> PAGINAS_DO_MENU =
            List.of("/", "/cadastrar", "/consumir", "/alertas", "/relatorio", "/historico", "/produtos",
                    "/notificacoes");

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requisicoes -> requisicoes
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/tarefas/enviar-alertas").permitAll()
                        .requestMatchers("/notificacoes/confirmar", "/notificacoes/descadastrar").permitAll()
                        .requestMatchers("/login", "/usuarios/novo", "/saude", "/css/**", "/js/**",
                                "/favicon.ico", "/favicon.svg", "/apple-touch-icon.png",
                                "/apple-touch-icon-precomposed.png").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/tarefas/enviar-alertas"))
                .exceptionHandling(excecoes -> excecoes.accessDeniedHandler(recusaDeAcesso()))
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

    private static AccessDeniedHandler recusaDeAcesso() {
        AccessDeniedHandler padrao = new AccessDeniedHandlerImpl();
        AuthenticationTrustResolver resolvedor = new AuthenticationTrustResolverImpl();
        AccessDeniedHandler tokenVencidoNoLogin = (requisicao, resposta, excecao) -> {
            if (!(excecao instanceof CsrfException)) {
                padrao.handle(requisicao, resposta, excecao);
                return;
            }
            boolean logado = resolvedor.isAuthenticated(SecurityContextHolder.getContext().getAuthentication());
            resposta.sendRedirect(requisicao.getContextPath() + (logado ? "/" : "/login?expirou"));
        };
        LinkedHashMap<RequestMatcher, AccessDeniedHandler> porCaminho = new LinkedHashMap<>();
        porCaminho.put(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login"), tokenVencidoNoLogin);
        return new RequestMatcherDelegatingAccessDeniedHandler(porCaminho, padrao);
    }
}
