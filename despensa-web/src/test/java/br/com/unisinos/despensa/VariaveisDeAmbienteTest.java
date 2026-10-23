package br.com.unisinos.despensa;

import br.com.unisinos.despensa.config.DiagnosticoDeVariaveis;
import br.com.unisinos.despensa.email.BrevoEnviadorDeEmail;
import br.com.unisinos.despensa.email.EnviadorDeEmail;
import br.com.unisinos.despensa.model.ResultadoEnvioAlertas;
import br.com.unisinos.despensa.service.AlertaVencimentoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "despensa.indices.criar-ao-iniciar=false")
@AutoConfigureMockMvc
@ContextConfiguration(initializers = VariaveisDeAmbienteTest.AmbienteDoRender.class)
class VariaveisDeAmbienteTest {

    static final Map<String, Object> VARIAVEIS = Map.of(
            "MONGODB_URI", "mongodb://mongo-do-teste.exemplo:27017/qualquer",
            "BREVO_API_KEY", "chave-falsa-do-ambiente",
            "EMAIL_REMETENTE", "remetente@exemplo.com",
            "EMAIL_REMETENTE_NOME", "Despensa Teste",
            "ALERTAS_TOKEN", "token-falso-vindo-do-ambiente");

    @Autowired
    private Environment ambiente;

    @Autowired
    private EnviadorDeEmail enviador;

    @Autowired
    private MongoConnectionDetails conexaoMongo;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertaVencimentoService alertaVencimentoService;

    @Test
    void variaveisComOsNomesExatos_devemSerLidasDoAmbienteDoSistema() {
        assertEquals("chave-falsa-do-ambiente", ambiente.getProperty("despensa.email.brevo-api-key"));
        assertEquals("remetente@exemplo.com", ambiente.getProperty("despensa.email.remetente"));
        assertEquals("Despensa Teste", ambiente.getProperty("despensa.email.remetente-nome"));
        assertEquals("token-falso-vindo-do-ambiente", ambiente.getProperty("despensa.alertas.token"));
    }

    @Test
    void comBrevoApiKeyEEmailRemetenteNoAmbiente_oEnvioDeveFicarLigado() {
        assertInstanceOf(BrevoEnviadorDeEmail.class, enviador);
        assertTrue(enviador.ativo());
    }

    @Test
    void alertasTokenDoAmbiente_deveSerOTokenAceitoPeloEndpoint() throws Exception {
        when(alertaVencimentoService.executar()).thenReturn(Optional.of(new ResultadoEnvioAlertas(0, 0, 0)));

        mockMvc.perform(post("/tarefas/enviar-alertas").header("X-Alertas-Token", "token-falso-vindo-do-ambiente"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tarefas/enviar-alertas").header("X-Alertas-Token", "outro"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mongodbUriDoAmbiente_deveContinuarChegandoAoClienteDoMongo() {
        assertEquals("mongodb://mongo-do-teste.exemplo:27017/qualquer", ambiente.getProperty("spring.mongodb.uri"));
        assertEquals(List.of("mongo-do-teste.exemplo:27017"), conexaoMongo.getConnectionString().getHosts());
    }

    @Test
    void diagnostico_deveListarAsVariaveisSemMostrarOsValores() {
        List<String> linhas = DiagnosticoDeVariaveis.descrever(Map.of(
                "BREVO_API_KEY", "xkeysib-segredo",
                "EMAIL_REMETENTE", "   ",
                "Alertas_Token ", "abc"), DiagnosticoDeVariaveis.VARIAVEIS_DE_EMAIL);

        assertTrue(linhas.contains("BREVO_API_KEY: definida (15 caracteres)"));
        assertTrue(linhas.contains("EMAIL_REMETENTE: definida, mas vazia"));
        assertTrue(linhas.contains("EMAIL_REMETENTE_NOME: não encontrada no ambiente"));
        assertTrue(linhas.contains("ALERTAS_TOKEN: não encontrada no ambiente"));
        assertTrue(linhas.stream().anyMatch(l -> l.contains("\"Alertas_Token \"") && l.contains("exatamente ALERTAS_TOKEN")));
        assertTrue(linhas.stream().noneMatch(l -> l.contains("xkeysib-segredo") || l.contains("abc")));
    }

    static class AmbienteDoRender implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext contexto) {
            ConfigurableEnvironment ambiente = contexto.getEnvironment();
            Map<String, Object> sistema = new HashMap<>(ambiente.getSystemEnvironment());
            DiagnosticoDeVariaveis.VARIAVEIS_DE_EMAIL.forEach(sistema::remove);
            sistema.remove("MONGODB_URI");
            sistema.putAll(VARIAVEIS);
            ambiente.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            sistema));
        }
    }
}
