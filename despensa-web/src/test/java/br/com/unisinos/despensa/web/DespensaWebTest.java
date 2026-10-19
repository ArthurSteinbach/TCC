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

class DespensaWebTest extends TesteWebBase {

    @Test
    void telaPrincipal_deveMostrarSoOsLotesDoUsuarioLogado() throws Exception {
        Produto daAna = produtoService.cadastrar(ANA.getId(), "111", "Leite da Ana", null);
        loteService.cadastrar(ANA.getId(), daAna.getId(), 2, 5.0, HOJE.plusDays(20));
        Produto doBruno = produtoService.cadastrar(BRUNO.getId(), "222", "Café do Bruno", null);
        loteService.cadastrar(BRUNO.getId(), doBruno.getId(), 1, 20.0, HOJE.plusDays(20));

        mockMvc.perform(get("/").param("usuarioId", BRUNO.getId()).with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Leite da Ana")))
                .andExpect(content().string(not(containsString("Café do Bruno"))));
    }

    @Test
    void telaPrincipal_deveMostrarOsLotesEmOrdemFefoComAsCoresDaVersaoDesktop() throws Exception {
        Produto produto = produtoService.cadastrar(ANA.getId(), "111", "Arroz", null);
        loteService.cadastrar(ANA.getId(), produto.getId(), 1, 10.0, HOJE.plusDays(30));
        Lote vencido = loteService.cadastrar(ANA.getId(), produto.getId(), 1, 10.0, HOJE.plusDays(1));
        loteService.editar(ANA.getId(), produto.getId(), vencido.getId(), 1, 10.0, HOJE.minusDays(2));
        loteService.cadastrar(ANA.getId(), produto.getId(), 1, 10.0, HOJE.plusDays(3));

        String html = mockMvc.perform(get("/").with(user(ANA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int vencidoNaTabela = html.indexOf("<tr class=\"item-vencido\"");
        int vencendoNaTabela = html.indexOf("<tr class=\"item-vencendo\"");
        int normalNaTabela = html.indexOf("<tr class=\"item-normal\"");
        assertTrue(vencidoNaTabela > 0 && vencidoNaTabela < vencendoNaTabela && vencendoNaTabela < normalNaTabela);
        assertTrue(html.contains("Vencido há 2 dias"));
        assertTrue(html.contains("3 dias"));
        assertTrue(html.contains("card mb-3 cartao-item item-vencido"));
    }

    @Test
    void telaPrincipal_deveAvisarOsItensEmAlertaEMostrarOTotalDePerdas() throws Exception {
        Produto produto = produtoService.cadastrar(ANA.getId(), "111", "Iogurte", null);
        loteService.cadastrar(ANA.getId(), produto.getId(), 1, 4.0, HOJE.plusDays(2));
        loteService.cadastrar(ANA.getId(), produto.getId(), 1, 4.0, HOJE.plusDays(7));
        loteService.cadastrar(ANA.getId(), produto.getId(), 1, 4.0, HOJE.plusDays(8));
        Lote descartado = loteService.cadastrar(ANA.getId(), produto.getId(), 5, 2.5, HOJE.plusDays(1));
        loteService.descartar(ANA.getId(), produto.getId(), descartado.getId());

        mockMvc.perform(get("/").with(user(ANA)))
                .andExpect(content().string(containsString("Você tem <strong>2</strong> item(ns)")))
                .andExpect(content().string(containsString("R$ 12,50")));
    }

    @Test
    void telaPrincipal_semItens_deveMostrarMensagemESemAviso() throws Exception {
        mockMvc.perform(get("/").with(user(ANA)))
                .andExpect(content().string(containsString("Nenhum item na despensa.")))
                .andExpect(content().string(not(containsString("item(ns) vencidos"))))
                .andExpect(content().string(containsString("R$ 0,00")));
    }

    @Test
    void telaPrincipal_deveTerOMenuComTodosOsItens() throws Exception {
        String html = mockMvc.perform(get("/").with(user(ANA)))
                .andReturn().getResponse().getContentAsString();

        for (String item : new String[]{">Despensa<", ">Cadastrar<", ">Consumir<", ">Alertas<", ">Relatório<",
                ">Produtos<", ">Sair<"}) {
            assertTrue(html.contains(item), "Menu sem o item " + item);
        }
    }

    @Test
    void itemDoMenuAindaNaoFeito_deveMostrarPaginaEmConstrucao() throws Exception {
        mockMvc.perform(get("/alertas").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Esta página está em construção.")));
    }

    @Test
    void botaoConsumido_deveDarBaixaNoLoteEVoltarParaADespensa() throws Exception {
        Produto produto = produtoService.cadastrar(ANA.getId(), "111", "Feijão", null);
        Lote lote = loteService.cadastrar(ANA.getId(), produto.getId(), 1, 8.0, HOJE.plusDays(10));

        mockMvc.perform(post("/lotes/{p}/{l}/consumir", produto.getId(), lote.getId()).with(user(ANA)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", "Item marcado como consumido."));

        assertEquals(StatusLote.CONSUMIDO, statusDoLote(ANA.getId(), produto, lote));
    }

    @Test
    void botaoDescartado_deveDarBaixaNoLoteESomarNasPerdas() throws Exception {
        Produto produto = produtoService.cadastrar(ANA.getId(), "111", "Feijão", null);
        Lote lote = loteService.cadastrar(ANA.getId(), produto.getId(), 2, 8.0, HOJE.plusDays(10));

        mockMvc.perform(post("/lotes/{p}/{l}/descartar", produto.getId(), lote.getId()).with(user(ANA)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", "Item marcado como descartado."));

        assertEquals(StatusLote.DESCARTADO, statusDoLote(ANA.getId(), produto, lote));
        assertEquals(16.0, loteService.calcularPerdaFinanceiraTotal(ANA.getId()), 0.001);
    }

    @Test
    void botoesDosLotes_devemPedirConfirmacao() throws Exception {
        Produto produto = produtoService.cadastrar(ANA.getId(), "111", "Feijão", null);
        loteService.cadastrar(ANA.getId(), produto.getId(), 1, 8.0, HOJE.plusDays(10));

        mockMvc.perform(get("/").with(user(ANA)))
                .andExpect(content().string(containsString("data-confirmacao=\"Marcar &quot;Feijão&quot; como consumido?\"")))
                .andExpect(content().string(containsString("como descartado (vencido)?")));
    }

    @Test
    void baixaNoLoteDeOutroUsuario_deveSerRecusada() throws Exception {
        Produto doBruno = produtoService.cadastrar(BRUNO.getId(), "222", "Café do Bruno", null);
        Lote lote = loteService.cadastrar(BRUNO.getId(), doBruno.getId(), 1, 20.0, HOJE.plusDays(10));

        mockMvc.perform(post("/lotes/{p}/{l}/descartar", doBruno.getId(), lote.getId()).with(user(ANA)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("erro", "Produto não encontrado."));

        assertEquals(StatusLote.ATIVO, statusDoLote(BRUNO.getId(), doBruno, lote));
    }

    @Test
    void baixaComItemAlteradoEmOutraTela_deveMostrarAvisoParaAtualizar() throws Exception {
        Produto produto = produtoService.cadastrar(ANA.getId(), "111", "Feijão", null);
        Lote lote = loteService.cadastrar(ANA.getId(), produto.getId(), 1, 8.0, HOJE.plusDays(10));
        repositorio().simularAlteracaoConcorrenteNaProximaGravacao();

        mockMvc.perform(post("/lotes/{p}/{l}/consumir", produto.getId(), lote.getId()).with(user(ANA)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("erro", "Este item foi alterado em outra tela, atualize a página"));

        assertEquals(StatusLote.ATIVO, statusDoLote(ANA.getId(), produto, lote));
    }

    @Test
    void bancoForaDoAr_deveMostrarMensagemAmigavel() throws Exception {
        repositorio().simularBancoForaDoAr(true);

        mockMvc.perform(get("/").with(user(ANA)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("Não foi possível acessar o banco de dados agora.")))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    private StatusLote statusDoLote(String usuarioId, Produto produto, Lote lote) {
        return produtoService.buscarPorId(usuarioId, produto.getId()).buscarLote(lote.getId()).orElseThrow().getStatus();
    }
}
