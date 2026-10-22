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

class ConsumoWebTest extends TesteWebBase {

    @Test
    void lista_deveTerSoOsProdutosDoUsuarioComLotesAtivos() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "7891000100103", "Leite da Ana", null);
        lote(ANA, leite, 3, 4.0, HOJE.plusDays(5));
        produtoService.cadastrar(ANA.getId(), "222", "Produto sem lotes", null);
        Produto consumido = produtoService.cadastrar(ANA.getId(), "333", "Produto todo consumido", null);
        Lote loteConsumido = lote(ANA, consumido, 1, 1.0, HOJE.plusDays(5));
        loteService.consumir(ANA.getId(), consumido.getId(), loteConsumido.getId());
        Produto doBruno = produtoService.cadastrar(BRUNO.getId(), "444", "Café do Bruno", null);
        lote(BRUNO, doBruno, 1, 1.0, HOJE.plusDays(5));

        mockMvc.perform(get("/consumir").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Leite da Ana — 3 disponíveis")))
                .andExpect(content().string(containsString("data-codigo=\"7891000100103\"")))
                .andExpect(content().string(not(containsString("Produto sem lotes"))))
                .andExpect(content().string(not(containsString("Produto todo consumido"))))
                .andExpect(content().string(not(containsString("Café do Bruno"))));
    }

    @Test
    void pagina_deveUsarOMesmoLeitorDeCodigoDeBarras() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        lote(ANA, leite, 1, 4.0, HOJE.plusDays(5));

        mockMvc.perform(get("/consumir").with(user(ANA)))
                .andExpect(content().string(containsString("Ler código de barras")))
                .andExpect(content().string(containsString("/js/leitor-codigo-barras.js")))
                .andExpect(content().string(containsString("/js/consumir.js")));
    }

    @Test
    void semProdutosNaDespensa_deveAvisarESugerirOCadastro() throws Exception {
        mockMvc.perform(get("/consumir").with(user(ANA)))
                .andExpect(content().string(containsString("Nenhum produto com itens na despensa.")));
    }

    @Test
    void previa_deveMostrarOsLotesNaOrdemFefoSemGravarNada() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        Lote distante = lote(ANA, leite, 4, 4.0, HOJE.plusDays(20));
        Lote proximo = lote(ANA, leite, 3, 4.0, HOJE.plusDays(2));
        int gravacoesAntes = repositorio().getGravacoes();

        String html = mockMvc.perform(get("/consumir").with(user(ANA))
                        .param("produtoId", leite.getId()).param("quantidade", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Confira antes de confirmar")))
                .andExpect(content().string(containsString("O lote inteiro será usado.")))
                .andExpect(content().string(containsString("Ficam 2 neste lote.")))
                .andExpect(content().string(containsString("Sim, consumir 5")))
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.indexOf("20/10/2026") < html.indexOf("07/11/2026"), "FEFO: o lote que vence antes vem primeiro");
        assertEquals(gravacoesAntes, repositorio().getGravacoes());
        assertEquals(StatusLote.ATIVO, recarregar(ANA, leite, proximo).getStatus());
        assertEquals(4, recarregar(ANA, leite, distante).getQuantidade());
    }

    @Test
    void previa_devePerguntarExatamenteOQueVaiAcontecerELimitarAQuantidadeAoDisponivel() throws Exception {
        Produto agua = produtoService.cadastrar(ANA.getId(), "111", "Água Mineral", null);
        lote(ANA, agua, 4, 2.0, HOJE.plusDays(10));
        loteVencido(ANA, agua, 5, 2.0, 1);

        mockMvc.perform(get("/consumir").with(user(ANA)).param("produtoId", agua.getId()).param("quantidade", "1"))
                .andExpect(content().string(containsString("Consumir 1 de 4 unidades de Água Mineral?")))
                .andExpect(content().string(containsString("max=\"4\"")))
                .andExpect(content().string(containsString("data-disponivel=\"4\"")));
    }

    @Test
    void origemInvalida_deveVoltarParaADespensa() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        lote(ANA, leite, 3, 4.0, HOJE.plusDays(5));

        mockMvc.perform(post("/consumir").with(user(ANA)).with(csrf()).param("produtoId", leite.getId())
                        .param("quantidade", "1").param("origem", "https://site-malicioso.example"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void confirmar_deveConsumirPeloFefoEVoltarParaADespensa() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        Lote distante = lote(ANA, leite, 4, 4.0, HOJE.plusDays(20));
        Lote proximo = lote(ANA, leite, 3, 4.0, HOJE.plusDays(2));

        mockMvc.perform(post("/consumir").with(user(ANA)).with(csrf())
                        .param("produtoId", leite.getId()).param("quantidade", "5"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", "Consumo registrado: 5 unidades de \"Leite\"."));

        assertEquals(StatusLote.CONSUMIDO, recarregar(ANA, leite, proximo).getStatus());
        Lote restante = recarregar(ANA, leite, distante);
        assertEquals(StatusLote.ATIVO, restante.getStatus());
        assertEquals(2, restante.getQuantidade());
    }

    @Test
    void quantidadeMaiorQueODisponivel_deveMostrarQuantoHaENaoGravar() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        lote(ANA, leite, 3, 4.0, HOJE.plusDays(5));
        int gravacoesAntes = repositorio().getGravacoes();

        mockMvc.perform(get("/consumir").with(user(ANA)).param("produtoId", leite.getId()).param("quantidade", "4"))
                .andExpect(content().string(containsString("há apenas 3 unidade(s) dentro da validade")))
                .andExpect(content().string(not(containsString("Sim, consumir"))));
        mockMvc.perform(post("/consumir").with(user(ANA)).with(csrf())
                        .param("produtoId", leite.getId()).param("quantidade", "4"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("há apenas 3 unidade(s) dentro da validade")));

        assertEquals(gravacoesAntes, repositorio().getGravacoes());
    }

    @Test
    void produtoSoComLotesVencidos_deveSugerirODescarte() throws Exception {
        Produto iogurte = produtoService.cadastrar(ANA.getId(), "111", "Iogurte", null);
        loteVencido(ANA, iogurte, 2, 3.0, 2);

        mockMvc.perform(get("/consumir").with(user(ANA)))
                .andExpect(content().string(containsString("Iogurte — só itens vencidos")));
        mockMvc.perform(get("/consumir").with(user(ANA)).param("produtoId", iogurte.getId()).param("quantidade", "1"))
                .andExpect(content().string(containsString("Todos os lotes ativos deste produto estão vencidos")))
                .andExpect(content().string(containsString("marque-os como descartados")));
    }

    @Test
    void produtoSemLotes_deveAvisarQueNaoHaItensNaDespensa() throws Exception {
        Produto vazio = produtoService.cadastrar(ANA.getId(), "111", "Arroz", null);

        mockMvc.perform(get("/consumir").with(user(ANA)).param("produtoId", vazio.getId()).param("quantidade", "1"))
                .andExpect(content().string(containsString("Este produto não tem lotes ativos na despensa.")));
    }

    @Test
    void produtoDeOutroUsuario_naoPodeSerConsumido() throws Exception {
        Produto doBruno = produtoService.cadastrar(BRUNO.getId(), "111", "Café do Bruno", null);
        Lote lote = lote(BRUNO, doBruno, 2, 10.0, HOJE.plusDays(5));

        mockMvc.perform(get("/consumir").with(user(ANA)).param("produtoId", doBruno.getId()).param("quantidade", "1"))
                .andExpect(content().string(containsString("Produto não encontrado.")))
                .andExpect(content().string(not(containsString("Café do Bruno"))));
        mockMvc.perform(post("/consumir").with(user(ANA)).with(csrf())
                        .param("produtoId", doBruno.getId()).param("quantidade", "1"))
                .andExpect(content().string(containsString("Produto não encontrado.")));

        assertEquals(StatusLote.ATIVO, recarregar(BRUNO, doBruno, lote).getStatus());
    }

    @Test
    void semEscolherProdutoOuComQuantidadeInvalida_deveMostrarAMensagemCerta() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        lote(ANA, leite, 3, 4.0, HOJE.plusDays(5));

        mockMvc.perform(get("/consumir").with(user(ANA)).param("produtoId", "").param("quantidade", "1"))
                .andExpect(content().string(containsString("Escolha o produto.")));
        mockMvc.perform(get("/consumir").with(user(ANA)).param("produtoId", leite.getId()).param("quantidade", "0"))
                .andExpect(content().string(containsString("Informe a quantidade (um número inteiro maior que zero).")));
    }

    @Test
    void confirmarComItemAlteradoEmOutraTela_deveAvisarParaAtualizar() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        Lote lote = lote(ANA, leite, 3, 4.0, HOJE.plusDays(5));
        repositorio().simularAlteracaoConcorrenteNaProximaGravacao();

        mockMvc.perform(post("/consumir").with(user(ANA)).with(csrf())
                        .param("produtoId", leite.getId()).param("quantidade", "1"))
                .andExpect(content().string(containsString("Este item foi alterado em outra tela, atualize a página")));

        assertEquals(3, recarregar(ANA, leite, lote).getQuantidade());
    }
}
