package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.StatusLote;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertasWebTest extends TesteWebBase {

    @Test
    void alertas_deveMostrarSoOsVencidosEOsQueVencemEmAte7DiasComAsCores() throws Exception {
        Produto vencido = produtoService.cadastrar(ANA.getId(), "1", "Iogurte vencido", null);
        loteVencido(ANA, vencido, 1, 3.0, 2);
        Produto vencendo = produtoService.cadastrar(ANA.getId(), "2", "Leite vencendo", null);
        lote(ANA, vencendo, 1, 4.0, HOJE.plusDays(7));
        Produto emDia = produtoService.cadastrar(ANA.getId(), "3", "Arroz em dia", null);
        lote(ANA, emDia, 1, 20.0, HOJE.plusDays(8));

        String html = mockMvc.perform(get("/alertas").with(user(ANA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("Iogurte vencido"));
        assertTrue(html.contains("Leite vencendo"));
        assertTrue(!html.contains("Arroz em dia"));
        assertTrue(html.contains("<tr class=\"item-vencido\""));
        assertTrue(html.contains("<tr class=\"item-vencendo\""));
        assertTrue(!html.contains("<tr class=\"item-normal\""));
        assertTrue(html.contains(">Consumido<") && html.contains(">Descartado<"));
        assertTrue(html.indexOf("Iogurte vencido") < html.indexOf("Leite vencendo"));
    }

    @Test
    void alertas_naoDeveMostrarItensDeOutroUsuario() throws Exception {
        Produto doBruno = produtoService.cadastrar(BRUNO.getId(), "1", "Café do Bruno", null);
        loteVencido(BRUNO, doBruno, 1, 3.0, 1);

        mockMvc.perform(get("/alertas").with(user(ANA)))
                .andExpect(content().string(not(containsString("Café do Bruno"))))
                .andExpect(content().string(containsString("Tudo em dia")));
    }

    @Test
    void semNenhumItemEmAlerta_deveMostrarTudoEmDia() throws Exception {
        Produto emDia = produtoService.cadastrar(ANA.getId(), "3", "Arroz", null);
        lote(ANA, emDia, 1, 20.0, HOJE.plusDays(30));

        mockMvc.perform(get("/alertas").with(user(ANA)))
                .andExpect(content().string(containsString("Tudo em dia")))
                .andExpect(content().string(not(containsString("Arroz"))));
    }

    @Test
    void botaoDescartadoNosAlertas_deveDarBaixaEVoltarParaOsAlertas() throws Exception {
        Produto vencido = produtoService.cadastrar(ANA.getId(), "1", "Iogurte", null);
        Lote lote = loteVencido(ANA, vencido, 2, 3.0, 1);

        mockMvc.perform(post("/lotes/{p}/{l}/descartar", vencido.getId(), lote.getId())
                        .with(user(ANA)).with(csrf()).param("origem", "alertas"))
                .andExpect(redirectedUrl("/alertas"))
                .andExpect(flash().attribute("sucesso", "Item marcado como descartado."));

        assertEquals(StatusLote.DESCARTADO, recarregar(ANA, vencido, lote).getStatus());
    }

    @Test
    void botoesDosAlertas_devemInformarQueVoltamParaOsAlertas() throws Exception {
        Produto vencendo = produtoService.cadastrar(ANA.getId(), "2", "Leite", null);
        lote(ANA, vencendo, 1, 4.0, HOJE.plusDays(1));

        mockMvc.perform(get("/alertas").with(user(ANA)))
                .andExpect(content().string(containsString("name=\"origem\" value=\"alertas\"")));
    }

    @Test
    void origemDesconhecida_deveVoltarParaADespensaSemSeguirEnderecosExternos() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "2", "Leite", null);
        Lote lote = lote(ANA, leite, 1, 4.0, HOJE.plusDays(1));

        mockMvc.perform(post("/lotes/{p}/{l}/consumir", leite.getId(), lote.getId())
                        .with(user(ANA)).with(csrf()).param("origem", "https://site-malicioso.example"))
                .andExpect(redirectedUrl("/"));
    }
}
