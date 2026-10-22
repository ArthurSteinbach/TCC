package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.StatusLote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

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

    @ParameterizedTest
    @ValueSource(strings = {"/", "/cadastrar", "/consumir", "/alertas", "/relatorio", "/produtos"})
    void todosOsItensDoMenu_devemAbrirUmaPaginaDeVerdade(String pagina) throws Exception {
        mockMvc.perform(get(pagina).with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("em construção"))));
    }

    @Test
    void cadaLoteDaDespensa_deveTerOBotaoEditar() throws Exception {
        Produto produto = produtoService.cadastrar(ANA.getId(), "111", "Feijão", null);
        Lote lote = lote(ANA, produto, 1, 8.0, HOJE.plusDays(10));

        mockMvc.perform(get("/").with(user(ANA)))
                .andExpect(content().string(containsString(
                        "href=\"/lotes/" + produto.getId() + "/" + lote.getId() + "/editar\"")));
    }

    @Test
    void botaoConsumir_deveAbrirATelaDeConsumoComOProdutoEAQuantidade1() throws Exception {
        Produto agua = produtoService.cadastrar(ANA.getId(), "111", "Água Mineral", null);
        lote(ANA, agua, 4, 2.0, HOJE.plusDays(10));

        mockMvc.perform(get("/").with(user(ANA)))
                .andExpect(content().string(containsString(
                        "href=\"/consumir?produtoId=" + agua.getId() + "&amp;quantidade=1&amp;origem=despensa\"")))
                .andExpect(content().string(not(containsString(">Consumido<"))));
    }

    @Test
    void consumirUmaUnidadeAPartirDaDespensa_deveManterORestanteAtivo() throws Exception {
        Produto agua = produtoService.cadastrar(ANA.getId(), "111", "Água Mineral", null);
        Lote lote = lote(ANA, agua, 4, 2.0, HOJE.plusDays(10));

        mockMvc.perform(get("/consumir").with(user(ANA)).param("produtoId", agua.getId()).param("quantidade", "1")
                        .param("origem", "despensa"))
                .andExpect(content().string(containsString("Consumir 1 de 4 unidades de Água Mineral?")));
        mockMvc.perform(post("/consumir").with(user(ANA)).with(csrf()).param("produtoId", agua.getId())
                        .param("quantidade", "1").param("origem", "despensa"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", "Consumo registrado: 1 unidade de \"Água Mineral\"."));

        Lote restante = recarregar(ANA, agua, lote);
        assertEquals(StatusLote.ATIVO, restante.getStatus());
        assertEquals(3, restante.getQuantidade());
        List<Lote> lotes = produtoService.buscarPorId(ANA.getId(), agua.getId()).getLotes();
        assertEquals(2, lotes.size());
        assertTrue(lotes.stream().anyMatch(l -> l.getStatus() == StatusLote.CONSUMIDO && l.getQuantidade() == 1));
    }

    @Test
    void loteVencido_naoDeveOferecerConsumirSoDescartarEEditar() throws Exception {
        Produto iogurte = produtoService.cadastrar(ANA.getId(), "111", "Iogurte", null);
        loteVencido(ANA, iogurte, 2, 3.0, 1);

        mockMvc.perform(get("/").with(user(ANA)))
                .andExpect(content().string(not(containsString("/consumir?produtoId="))))
                .andExpect(content().string(containsString(">Descartar<")))
                .andExpect(content().string(containsString(">Editar<")));
    }

    @Test
    void botaoDescartado_deveDarBaixaNoLoteESomarNasPerdas() throws Exception {
        Produto produto = produtoService.cadastrar(ANA.getId(), "111", "Feijão", null);
        Lote lote = loteService.cadastrar(ANA.getId(), produto.getId(), 2, 8.0, HOJE.plusDays(10));

        mockMvc.perform(post("/lotes/{p}/{l}/descartar", produto.getId(), lote.getId()).with(user(ANA)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", "Descarte registrado: 2 de 2 unidades de \"Feijão\"."));

        assertEquals(StatusLote.DESCARTADO, statusDoLote(ANA.getId(), produto, lote));
        assertEquals(16.0, loteService.calcularPerdaFinanceiraTotal(ANA.getId()), 0.001);
    }

    @Test
    void botaoDescartar_deveAbrirATelaQuePedeAQuantidade() throws Exception {
        Produto produto = produtoService.cadastrar(ANA.getId(), "111", "Feijão", null);
        Lote lote = lote(ANA, produto, 1, 8.0, HOJE.plusDays(10));

        mockMvc.perform(get("/").with(user(ANA)))
                .andExpect(content().string(containsString(
                        "href=\"/lotes/" + produto.getId() + "/" + lote.getId() + "/descartar?origem=despensa\"")));
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

        mockMvc.perform(post("/lotes/{p}/{l}/descartar", produto.getId(), lote.getId()).with(user(ANA)).with(csrf()))
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
