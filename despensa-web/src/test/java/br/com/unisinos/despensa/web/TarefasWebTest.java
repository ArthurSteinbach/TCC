package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Produto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TarefasWebTest extends TesteWebBase {

    private static final String TOKEN = "token-de-teste-com-tamanho-razoavel";
    private static final String CABECALHO = "X-Alertas-Token";

    @BeforeEach
    void cadastrarLoteVencendo() {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        lote(ANA, leite, 2, 4.0, HOJE.plusDays(2));
    }

    @Test
    void semToken_deveResponder401SemDetalhesENaoEnviarNada() throws Exception {
        mockMvc.perform(post("/tarefas/enviar-alertas"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));

        assertTrue(emails().enviadas().isEmpty());
    }

    @Test
    void tokenErrado_deveResponder401SemDetalhesENaoEnviarNada() throws Exception {
        mockMvc.perform(post("/tarefas/enviar-alertas").header(CABECALHO, TOKEN + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        mockMvc.perform(post("/tarefas/enviar-alertas").header(CABECALHO, ""))
                .andExpect(status().isUnauthorized());

        assertTrue(emails().enviadas().isEmpty());
    }

    @Test
    void tokenCerto_semLoginESemCsrf_deveEnviarOsAvisosEDevolverOResumo() throws Exception {
        mockMvc.perform(post("/tarefas/enviar-alertas").header(CABECALHO, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuariosVerificados").value(2))
                .andExpect(jsonPath("$.emailsEnviados").value(1))
                .andExpect(jsonPath("$.falhas").value(0));

        assertEquals(1, emails().enviadas().size());
        assertEquals("ana@exemplo.com", emails().ultima().destinatario());
        assertTrue(emails().ultima().html().contains("https://despensa.teste/alertas"));
    }

    @Test
    void segundaChamadaNoMesmoDia_naoDeveReenviar() throws Exception {
        mockMvc.perform(post("/tarefas/enviar-alertas").header(CABECALHO, TOKEN)).andExpect(status().isOk());

        mockMvc.perform(post("/tarefas/enviar-alertas").header(CABECALHO, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailsEnviados").value(0));

        assertEquals(1, emails().enviadas().size());
    }

    @Test
    void terceiraChamadaNoMesmoDia_tambemNaoDeveReenviar() throws Exception {
        mockMvc.perform(post("/tarefas/enviar-alertas").header(CABECALHO, TOKEN))
                .andExpect(jsonPath("$.emailsEnviados").value(1));
        mockMvc.perform(post("/tarefas/enviar-alertas").header(CABECALHO, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailsEnviados").value(0))
                .andExpect(jsonPath("$.falhas").value(0));
        mockMvc.perform(post("/tarefas/enviar-alertas").header(CABECALHO, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailsEnviados").value(0))
                .andExpect(jsonPath("$.falhas").value(0));

        assertEquals(1, emails().enviadas().size());
    }

    @Test
    void envioDeEmailDesligado_deveResponder503() throws Exception {
        emails().desligar();

        mockMvc.perform(post("/tarefas/enviar-alertas").header(CABECALHO, TOKEN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.envioAtivo").value(false));
    }

    @Test
    void metodoGet_naoDeveDispararOsAvisos() throws Exception {
        mockMvc.perform(get("/tarefas/enviar-alertas").header(CABECALHO, TOKEN))
                .andExpect(redirectedUrl("/login"));

        assertTrue(emails().enviadas().isEmpty());
    }
}
