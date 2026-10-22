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

class HistoricoWebTest extends TesteWebBase {

    @Test
    void historico_deveListarConsumidosEDescartadosDoMaisRecenteParaOMaisAntigo() throws Exception {
        salvarProdutoComBaixas();

        String html = mockMvc.perform(get("/historico").with(user(ANA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int recente = html.indexOf("15/10/2026 18:40");
        int meio = html.indexOf("10/10/2026 12:00");
        int antigo = html.indexOf("01/10/2026 09:15");
        assertTrue(recente > 0 && recente < meio && meio < antigo, "Do mais recente para o mais antigo");
        assertTrue(html.contains(">Consumido<") && html.contains(">Descartado<"));
        assertTrue(html.contains(">7,50<") && html.contains(">6,00<") && html.contains(">2,00<"));
        assertTrue(!html.contains("Ativo que não é histórico"));
    }

    @Test
    void filtro_deveMostrarSoAsBaixasDaSituacaoEscolhida() throws Exception {
        salvarProdutoComBaixas();

        String consumidos = mockMvc.perform(get("/historico").param("situacao", "consumidos").with(user(ANA)))
                .andReturn().getResponse().getContentAsString();
        String descartados = mockMvc.perform(get("/historico").param("situacao", "descartados").with(user(ANA)))
                .andReturn().getResponse().getContentAsString();

        assertTrue(consumidos.contains("10/10/2026 12:00"));
        assertTrue(!consumidos.contains("15/10/2026 18:40") && !consumidos.contains("01/10/2026 09:15"));
        assertTrue(descartados.contains("15/10/2026 18:40") && descartados.contains("01/10/2026 09:15"));
        assertTrue(!descartados.contains("10/10/2026 12:00"));
    }

    @Test
    void filtroInvalido_deveMostrarTodos() throws Exception {
        salvarProdutoComBaixas();

        mockMvc.perform(get("/historico").param("situacao", "qualquer").with(user(ANA)))
                .andExpect(content().string(containsString("10/10/2026 12:00")))
                .andExpect(content().string(containsString("15/10/2026 18:40")));
    }

    @Test
    void historico_naoDeveMostrarBaixasDeOutroUsuario() throws Exception {
        Produto doBruno = produtoService.cadastrar(BRUNO.getId(), "1", "Café do Bruno", null);
        Lote lote = lote(BRUNO, doBruno, 2, 15.0, HOJE.plusDays(3));
        loteService.consumir(BRUNO.getId(), doBruno.getId(), lote.getId());

        mockMvc.perform(get("/historico").with(user(ANA)))
                .andExpect(content().string(not(containsString("Café do Bruno"))))
                .andExpect(content().string(containsString("Nenhum item no histórico.")));
    }

    @Test
    void historico_deveEstarNoMenu() throws Exception {
        mockMvc.perform(get("/").with(user(ANA)))
                .andExpect(content().string(containsString("href=\"/historico\"")))
                .andExpect(content().string(containsString(">Histórico<")));
    }

    private void salvarProdutoComBaixas() {
        Produto produto = new Produto(ANA.getId(), "111", "Iogurte", null, LocalDateTime.of(2026, 9, 1, 10, 0));
        produto.getLotes().add(baixa("a", 2, 3.0, StatusLote.DESCARTADO, LocalDateTime.of(2026, 10, 1, 9, 15)));
        produto.getLotes().add(baixa("b", 1, 7.5, StatusLote.DESCARTADO, LocalDateTime.of(2026, 10, 15, 18, 40)));
        produto.getLotes().add(baixa("c", 1, 2.0, StatusLote.CONSUMIDO, LocalDateTime.of(2026, 10, 10, 12, 0)));
        repositorio().save(produto);
        Produto ativo = produtoService.cadastrar(ANA.getId(), "222", "Ativo que não é histórico", null);
        lote(ANA, ativo, 1, 1.0, HOJE.plusDays(3));
    }

    private static Lote baixa(String id, int quantidade, double preco, StatusLote status, LocalDateTime dataBaixa) {
        Lote lote = new Lote(id, quantidade, preco, HOJE.plusDays(5), LocalDateTime.of(2026, 9, 1, 10, 0));
        lote.setStatus(status);
        lote.setDataBaixa(dataBaixa);
        return lote;
    }
}
