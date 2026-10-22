package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.StatusLote;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
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

class DescarteWebTest extends TesteWebBase {

    @Test
    void formulario_deveSugerirOLoteInteiroComMaximoIgualAoLoteEConfirmacaoClara() throws Exception {
        Produto agua = produtoService.cadastrar(ANA.getId(), "111", "Água Mineral", null);
        Lote lote = lote(ANA, agua, 4, 2.0, HOJE.plusDays(3));

        mockMvc.perform(get("/lotes/{p}/{l}/descartar", agua.getId(), lote.getId()).with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"4\" min=\"1\" max=\"4\"")))
                .andExpect(content().string(containsString(
                        "data-confirmacao=\"Descartar {quantidade} de 4 unidades de Água Mineral? Isso entra no total de perdas.\"")));
    }

    @Test
    void descarteParcial_deveDividirOLoteEContarSoAParteDescartadaNasPerdas() throws Exception {
        Produto agua = produtoService.cadastrar(ANA.getId(), "111", "Água Mineral", null);
        Lote lote = lote(ANA, agua, 4, 2.0, HOJE.plusDays(3));

        mockMvc.perform(post("/lotes/{p}/{l}/descartar", agua.getId(), lote.getId()).with(user(ANA)).with(csrf())
                        .param("quantidade", "1").param("origem", "despensa"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", "Descarte registrado: 1 de 4 unidades de \"Água Mineral\"."));

        Lote restante = recarregar(ANA, agua, lote);
        assertEquals(StatusLote.ATIVO, restante.getStatus());
        assertEquals(3, restante.getQuantidade());
        List<Lote> lotes = produtoService.buscarPorId(ANA.getId(), agua.getId()).getLotes();
        assertTrue(lotes.stream().anyMatch(l -> l.getStatus() == StatusLote.DESCARTADO && l.getQuantidade() == 1));
        assertEquals(2.0, loteService.calcularPerdaFinanceiraTotal(ANA.getId()), 0.001);
    }

    @Test
    void quantidadeMaiorQueOLote_deveMostrarMensagemNaPropriaTelaSemAlterar() throws Exception {
        Produto agua = produtoService.cadastrar(ANA.getId(), "111", "Água Mineral", null);
        Lote lote = lote(ANA, agua, 4, 2.0, HOJE.plusDays(3));

        mockMvc.perform(post("/lotes/{p}/{l}/descartar", agua.getId(), lote.getId()).with(user(ANA)).with(csrf())
                        .param("quantidade", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("não pode ser maior que a do lote (4)")));
        mockMvc.perform(post("/lotes/{p}/{l}/descartar", agua.getId(), lote.getId()).with(user(ANA)).with(csrf())
                        .param("quantidade", "0"))
                .andExpect(content().string(containsString("Informe a quantidade")));

        assertEquals(4, recarregar(ANA, agua, lote).getQuantidade());
        assertEquals(0.0, loteService.calcularPerdaFinanceiraTotal(ANA.getId()), 0.001);
    }

    @Test
    void loteDeOutroUsuarioOuJaBaixado_naoPodeSerDescartado() throws Exception {
        Produto doBruno = produtoService.cadastrar(BRUNO.getId(), "111", "Café do Bruno", null);
        Lote lote = lote(BRUNO, doBruno, 2, 10.0, HOJE.plusDays(5));
        Produto leite = produtoService.cadastrar(ANA.getId(), "222", "Leite", null);
        Lote baixado = lote(ANA, leite, 1, 4.0, HOJE.plusDays(5));
        loteService.descartar(ANA.getId(), leite.getId(), baixado.getId());

        mockMvc.perform(get("/lotes/{p}/{l}/descartar", doBruno.getId(), lote.getId()).with(user(ANA)))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("erro", "Produto não encontrado."));
        mockMvc.perform(post("/lotes/{p}/{l}/descartar", doBruno.getId(), lote.getId()).with(user(ANA)).with(csrf())
                        .param("quantidade", "1"))
                .andExpect(redirectedUrl("/"));
        mockMvc.perform(get("/lotes/{p}/{l}/descartar", leite.getId(), baixado.getId()).with(user(ANA))
                        .param("origem", "alertas"))
                .andExpect(redirectedUrl("/alertas"))
                .andExpect(flash().attribute("erro", "Lote não encontrado ou já baixado."));

        assertEquals(2, recarregar(BRUNO, doBruno, lote).getQuantidade());
    }

    @Test
    void descarteComItemAlteradoEmOutraTela_deveAvisarParaAtualizar() throws Exception {
        Produto agua = produtoService.cadastrar(ANA.getId(), "111", "Água Mineral", null);
        Lote lote = lote(ANA, agua, 4, 2.0, HOJE.plusDays(3));
        repositorio().simularAlteracaoConcorrenteNaProximaGravacao();

        mockMvc.perform(post("/lotes/{p}/{l}/descartar", agua.getId(), lote.getId()).with(user(ANA)).with(csrf())
                        .param("quantidade", "1"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("erro", "Este item foi alterado em outra tela, atualize a página"));

        assertEquals(4, recarregar(ANA, agua, lote).getQuantidade());
    }
}
