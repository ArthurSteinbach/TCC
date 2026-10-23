package br.com.unisinos.despensa.config;

import br.com.unisinos.despensa.email.BrevoEnviadorDeEmail;
import br.com.unisinos.despensa.email.EnviadorDeEmail;
import br.com.unisinos.despensa.email.EnvioDeEmailDesligado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class EmailConfig {

    private static final Logger LOG = LoggerFactory.getLogger(EmailConfig.class);
    private static final String NOME_PADRAO = "Controle de Despensa";

    @Bean
    public EnviadorDeEmail enviadorDeEmail(HttpClient clienteHttp, ConfigurableEnvironment ambiente,
                                           @Value("${despensa.email.brevo-api-key:}") String chaveApi,
                                           @Value("${despensa.email.remetente:}") String remetente,
                                           @Value("${despensa.email.remetente-nome:}") String nomeRemetente) {
        DiagnosticoDeVariaveis.descrever(variaveisDoSistema(ambiente), DiagnosticoDeVariaveis.VARIAVEIS_DE_EMAIL)
                .forEach(linha -> LOG.info("Variável de ambiente {}", linha));
        if (chaveApi.isBlank() || remetente.isBlank()) {
            LOG.warn("Envio de e-mail desligado: defina BREVO_API_KEY e EMAIL_REMETENTE para ligar os avisos.");
            return new EnvioDeEmailDesligado();
        }
        LOG.info("Envio de e-mail pela Brevo ligado.");
        return new BrevoEnviadorDeEmail(clienteHttp, chaveApi.trim(), remetente.trim(),
                nomeRemetente.isBlank() ? NOME_PADRAO : nomeRemetente.trim());
    }

    private static Map<String, String> variaveisDoSistema(ConfigurableEnvironment ambiente) {
        PropertySource<?> fonte = ambiente.getPropertySources()
                .get(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        if (fonte == null || !(fonte.getSource() instanceof Map<?, ?> valores)) {
            return System.getenv();
        }
        Map<String, String> variaveis = new HashMap<>();
        valores.forEach((nome, valor) -> variaveis.put(String.valueOf(nome), valor == null ? null : String.valueOf(valor)));
        return variaveis;
    }
}
