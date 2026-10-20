package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EdicaoLoteWebTest extends TesteWebBase {

    @Test
    void formulario_deveVirPreenchidoComOsDadosDoLote() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        Lote lote = lote(ANA, leite, 3, 4.5, LocalDate.of(2026, 11, 20));

        mockMvc.perform(get("/lotes/{p}/{l}/editar", leite.getId(), lote.getId()).with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Leite")))
                .andExpect(content().string(containsString("value=\"3\"")))
                .andExpect(content().string(containsString("value=\"4.50\"")))
                .andExpect(content().string(containsString("value=\"2026-11-20\"")));
    }

    @Test
    void salvar_deveAlterarQuantidadePrecoEValidadeEVoltarParaADespensa() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        Lote lote = lote(ANA, leite, 3, 4.5, HOJE.plusDays(5));

        mockMvc.perform(post("/lotes/{p}/{l}/editar", leite.getId(), lote.getId()).with(user(ANA)).with(csrf())
                        .param("quantidade", "2").param("precoUnitario", "5,25").param("validade", "2026-12-24"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", "Lote de \"Leite\" atualizado."));

        Lote editado = recarregar(ANA, leite, lote);
        assertEquals(2, editado.getQuantidade());
        assertEquals(5.25, editado.getPrecoUnitario(), 0.001);
        assertEquals(LocalDate.of(2026, 12, 24), editado.getDataValidade());
    }

    @Test
    void salvarComDadosInvalidos_deveMostrarMensagemEManterODigitado() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        Lote lote = lote(ANA, leite, 3, 4.5, HOJE.plusDays(5));

        mockMvc.perform(post("/lotes/{p}/{l}/editar", leite.getId(), lote.getId()).with(user(ANA)).with(csrf())
                        .param("quantidade", "0").param("precoUnitario", "5").param("validade", "2026-12-24"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe a quantidade (um número inteiro maior que zero).")))
                .andExpect(content().string(containsString("value=\"2026-12-24\"")));
        mockMvc.perform(post("/lotes/{p}/{l}/editar", leite.getId(), lote.getId()).with(user(ANA)).with(csrf())
                        .param("quantidade", "1").param("precoUnitario", "-1").param("validade", "2026-12-24"))
                .andExpect(content().string(containsString("O preço não pode ser negativo.")));
        mockMvc.perform(post("/lotes/{p}/{l}/editar", leite.getId(), lote.getId()).with(user(ANA)).with(csrf())
                        .param("quantidade", "1").param("precoUnitario", "5").param("validade", ""))
                .andExpect(content().string(containsString("Informe a data de validade.")));

        assertEquals(3, recarregar(ANA, leite, lote).getQuantidade());
    }

    @Test
    void loteDeOutroUsuario_naoPodeSerEditado() throws Exception {
        Produto doBruno = produtoService.cadastrar(BRUNO.getId(), "111", "Café do Bruno", null);
        Lote lote = lote(BRUNO, doBruno, 2, 10.0, HOJE.plusDays(5));

        mockMvc.perform(get("/lotes/{p}/{l}/editar", doBruno.getId(), lote.getId()).with(user(ANA)))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("erro", "Produto não encontrado."));
        mockMvc.perform(post("/lotes/{p}/{l}/editar", doBruno.getId(), lote.getId()).with(user(ANA)).with(csrf())
                        .param("quantidade", "99").param("precoUnitario", "1").param("validade", "2026-12-24"))
                .andExpect(redirectedUrl("/"));

        assertEquals(2, recarregar(BRUNO, doBruno, lote).getQuantidade());
    }

    @Test
    void loteJaBaixado_naoPodeSerEditado() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        Lote lote = lote(ANA, leite, 3, 4.5, HOJE.plusDays(5));
        loteService.consumir(ANA.getId(), leite.getId(), lote.getId());

        mockMvc.perform(get("/lotes/{p}/{l}/editar", leite.getId(), lote.getId()).with(user(ANA)))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("erro", "Lote não encontrado ou já baixado."));
    }

    @Test
    void salvarComItemAlteradoEmOutraTela_deveAvisarParaAtualizar() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        Lote lote = lote(ANA, leite, 3, 4.5, HOJE.plusDays(5));
        repositorio().simularAlteracaoConcorrenteNaProximaGravacao();

        mockMvc.perform(post("/lotes/{p}/{l}/editar", leite.getId(), lote.getId()).with(user(ANA)).with(csrf())
                        .param("quantidade", "2").param("precoUnitario", "5").param("validade", "2026-12-24"))
                .andExpect(content().string(containsString("Este item foi alterado em outra tela, atualize a página")));

        assertEquals(3, recarregar(ANA, leite, lote).getQuantidade());
    }
}
