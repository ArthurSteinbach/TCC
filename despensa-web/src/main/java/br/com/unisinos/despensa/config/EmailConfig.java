package br.com.unisinos.despensa.config;

import br.com.unisinos.despensa.email.BrevoEnviadorDeEmail;
import br.com.unisinos.despensa.email.EnviadorDeEmail;
import br.com.unisinos.despensa.email.EnvioDeEmailDesligado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class EmailConfig {

    private static final Logger LOG = LoggerFactory.getLogger(EmailConfig.class);
    private static final String NOME_PADRAO = "Controle de Despensa";

    @Bean
    public EnviadorDeEmail enviadorDeEmail(HttpClient clienteHttp,
                                           @Value("${despensa.email.brevo-api-key:}") String chaveApi,
                                           @Value("${despensa.email.remetente:}") String remetente,
                                           @Value("${despensa.email.remetente-nome:}") String nomeRemetente) {
        if (chaveApi.isBlank() || remetente.isBlank()) {
            LOG.warn("Envio de e-mail desligado: defina BREVO_API_KEY e EMAIL_REMETENTE para ligar os avisos.");
            return new EnvioDeEmailDesligado();
        }
        LOG.info("Envio de e-mail pela Brevo ligado.");
        return new BrevoEnviadorDeEmail(clienteHttp, chaveApi.trim(), remetente.trim(),
                nomeRemetente.isBlank() ? NOME_PADRAO : nomeRemetente.trim());
    }
}
