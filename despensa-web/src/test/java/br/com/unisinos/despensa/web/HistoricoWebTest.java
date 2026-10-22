package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.StatusLote;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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

    @Test
    void cadaItem_deveTerOBotaoDesfazerComConfirmacaoClara() throws Exception {
        Produto agua = produtoService.cadastrar(ANA.getId(), "111", "Água Mineral", null);
        Lote lote = lote(ANA, agua, 4, 2.0, HOJE.plusDays(3));
        loteService.descartar(ANA.getId(), agua.getId(), lote.getId(), 1);
        loteService.consumirPorFEFO(ANA.getId(), agua.getId(), 2);

        mockMvc.perform(get("/historico").with(user(ANA)))
                .andExpect(content().string(containsString(
                        "Desfazer: devolver 1 unidade de Água Mineral para a despensa? O valor sai do total de perdas.")))
                .andExpect(content().string(containsString(
                        "data-confirmacao=\"Desfazer: devolver 2 unidades de Água Mineral para a despensa?\"")));
    }

    @Test
    void desfazerConsumo_deveDevolverParaADespensaETirarDoHistorico() throws Exception {
        Produto agua = produtoService.cadastrar(ANA.getId(), "111", "Água Mineral", null);
        Lote lote = lote(ANA, agua, 4, 2.0, HOJE.plusDays(3));
        loteService.consumirPorFEFO(ANA.getId(), agua.getId(), 1);
        Lote consumido = produtoService.buscarPorId(ANA.getId(), agua.getId()).getLotes().stream()
                .filter(l -> l.getStatus() == StatusLote.CONSUMIDO).findFirst().orElseThrow();

        mockMvc.perform(post("/historico/{p}/{l}/desfazer", agua.getId(), consumido.getId())
                        .with(user(ANA)).with(csrf()).param("situacao", "consumidos"))
                .andExpect(redirectedUrl("/historico?situacao=consumidos"))
                .andExpect(flash().attribute("sucesso",
                        "Desfeito: 1 unidade de \"Água Mineral\" voltou para a despensa."));

        assertEquals(4, recarregar(ANA, agua, lote).getQuantidade());
        assertTrue(loteService.listarHistorico(ANA.getId(), null).isEmpty());
    }

    @Test
    void desfazerDescarte_deveVoltarOTotalDePerdasAoValorAnterior() throws Exception {
        Produto agua = produtoService.cadastrar(ANA.getId(), "111", "Água Mineral", null);
        Lote outro = lote(ANA, agua, 1, 10.0, HOJE.plusDays(1));
        loteService.descartar(ANA.getId(), agua.getId(), outro.getId());
        double perdaAntes = loteService.calcularPerdaFinanceiraTotal(ANA.getId());
        Lote lote = lote(ANA, agua, 4, 2.0, HOJE.plusDays(3));
        Lote descartado = loteService.descartar(ANA.getId(), agua.getId(), lote.getId(), 3);
        assertEquals(perdaAntes + 6.0, loteService.calcularPerdaFinanceiraTotal(ANA.getId()), 0.001);

        mockMvc.perform(post("/historico/{p}/{l}/desfazer", agua.getId(), descartado.getId())
                        .with(user(ANA)).with(csrf()))
                .andExpect(redirectedUrl("/historico"));

        assertEquals(perdaAntes, loteService.calcularPerdaFinanceiraTotal(ANA.getId()), 0.001);
        assertEquals(4, recarregar(ANA, agua, lote).getQuantidade());
        mockMvc.perform(get("/relatorio").with(user(ANA)))
                .andExpect(content().string(containsString("R$ 10,00")));
    }

    @Test
    void desfazerItemDeOutroUsuario_deveSerRecusado() throws Exception {
        Produto doBruno = produtoService.cadastrar(BRUNO.getId(), "1", "Café do Bruno", null);
        Lote lote = lote(BRUNO, doBruno, 2, 15.0, HOJE.plusDays(3));
        loteService.descartar(BRUNO.getId(), doBruno.getId(), lote.getId());

        mockMvc.perform(post("/historico/{p}/{l}/desfazer", doBruno.getId(), lote.getId())
                        .with(user(ANA)).with(csrf()))
                .andExpect(redirectedUrl("/historico"))
                .andExpect(flash().attribute("erro", "Produto não encontrado."));

        assertEquals(StatusLote.DESCARTADO, recarregar(BRUNO, doBruno, lote).getStatus());
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
