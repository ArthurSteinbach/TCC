package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.StatusLote;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RelatorioWebTest extends TesteWebBase {

    @Test
    void relatorio_deveListarOsDescartadosDoMaisRecenteParaOMaisAntigoComOTotalNoTopo() throws Exception {
        Produto produto = new Produto(ANA.getId(), "111", "Iogurte", null, LocalDateTime.of(2026, 9, 1, 10, 0));
        produto.getLotes().add(descartado("a", 2, 3.0, LocalDateTime.of(2026, 10, 1, 9, 15)));
        produto.getLotes().add(descartado("b", 1, 7.5, LocalDateTime.of(2026, 10, 15, 18, 40)));
        produto.getLotes().add(descartado("c", 4, 1.25, LocalDateTime.of(2026, 9, 20, 8, 0)));
        Lote consumido = descartado("d", 9, 9.0, LocalDateTime.of(2026, 10, 16, 8, 0));
        consumido.setStatus(StatusLote.CONSUMIDO);
        produto.getLotes().add(consumido);
        repositorio().save(produto);

        String html = mockMvc.perform(get("/relatorio").with(user(ANA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int total = html.indexOf("R$ 18,50");
        int maisRecente = html.indexOf("15/10/2026 18:40");
        int meio = html.indexOf("01/10/2026 09:15");
        int maisAntigo = html.indexOf("20/09/2026 08:00");
        assertTrue(total > 0 && total < maisRecente, "O total deve aparecer no topo");
        assertTrue(maisRecente < meio && meio < maisAntigo, "Do mais recente para o mais antigo");
        assertTrue(html.contains(">7,50<") && html.contains(">6,00<") && html.contains(">5,00<"));
        assertTrue(!html.contains("16/10/2026"), "Lote consumido não é perda");
    }

    @Test
    void relatorio_naoDeveMostrarDescartesDeOutroUsuario() throws Exception {
        Produto doBruno = produtoService.cadastrar(BRUNO.getId(), "1", "Café do Bruno", null);
        Lote lote = lote(BRUNO, doBruno, 2, 15.0, HOJE.plusDays(3));
        loteService.descartar(BRUNO.getId(), doBruno.getId(), lote.getId());

        mockMvc.perform(get("/relatorio").with(user(ANA)))
                .andExpect(content().string(not(containsString("Café do Bruno"))))
                .andExpect(content().string(containsString("R$ 0,00")))
                .andExpect(content().string(containsString("Nenhum item descartado.")));
    }

    @Test
    void relatorio_deveMostrarADataDoDescarteNoHorarioDeBrasilia() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "1", "Leite", null);
        Lote lote = lote(ANA, leite, 1, 4.0, HOJE.plusDays(3));
        loteService.descartar(ANA.getId(), leite.getId(), lote.getId());

        mockMvc.perform(get("/relatorio").with(user(ANA)))
                .andExpect(content().string(containsString("18/10/2026 22:30")))
                .andExpect(content().string(containsString("R$ 4,00")));
    }

    private static Lote descartado(String id, int quantidade, double preco, LocalDateTime dataBaixa) {
        Lote lote = new Lote(id, quantidade, preco, HOJE.minusDays(1), LocalDateTime.of(2026, 9, 1, 10, 0));
        lote.setStatus(StatusLote.DESCARTADO);
        lote.setDataBaixa(dataBaixa);
        return lote;
    }
}
