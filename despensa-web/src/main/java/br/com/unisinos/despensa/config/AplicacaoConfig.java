package br.com.unisinos.despensa.config;

import br.com.unisinos.despensa.util.Constantes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class AplicacaoConfig {

    @Bean
    public Clock relogio() {
        return Clock.system(Constantes.FUSO_HORARIO);
    }

    @Bean
    public HttpClient clienteHttp() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
}
