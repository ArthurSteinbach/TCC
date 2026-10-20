package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
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

class ProdutosWebTest extends TesteWebBase {

    @Test
    void lista_deveMostrarSoOsProdutosDoUsuarioComCodigoNomeECategoria() throws Exception {
        produtoService.cadastrar(ANA.getId(), "7891000100103", "Leite da Ana", "Laticínios");
        produtoService.cadastrar(BRUNO.getId(), "7896005800010", "Café do Bruno", "Bebidas");

        mockMvc.perform(get("/produtos").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("7891000100103")))
                .andExpect(content().string(containsString("Leite da Ana")))
                .andExpect(content().string(containsString("Laticínios")))
                .andExpect(content().string(not(containsString("Café do Bruno"))))
                .andExpect(content().string(not(containsString("7896005800010"))));
    }

    @Test
    void excluir_devePedirConfirmacao() throws Exception {
        produtoService.cadastrar(ANA.getId(), "111", "Leite", null);

        mockMvc.perform(get("/produtos").with(user(ANA)))
                .andExpect(content().string(containsString("data-confirmacao=\"Excluir o produto &quot;Leite&quot;?\"")));
    }

    @Test
    void formularioDeEdicao_deveMostrarOCodigoSoParaLeitura() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "7891000100103", "Leite", "Laticínios");

        String html = mockMvc.perform(get("/produtos/{id}/editar", leite.getId()).with(user(ANA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("value=\"7891000100103\" readonly"));
        assertTrue(!html.contains("name=\"codigoBarras\""));
        assertTrue(html.contains("value=\"Leite\""));
    }

    @Test
    void editar_deveSalvarNomeECategoriaSemMudarOCodigo() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "7891000100103", "Leite", "Laticínios");

        mockMvc.perform(post("/produtos/{id}/editar", leite.getId()).with(user(ANA)).with(csrf())
                        .param("nome", "Leite Desnatado").param("categoria", "Bebidas")
                        .param("codigoBarras", "999"))
                .andExpect(redirectedUrl("/produtos"))
                .andExpect(flash().attribute("sucesso", "Produto \"Leite Desnatado\" atualizado."));

        Produto editado = produtoService.buscarPorId(ANA.getId(), leite.getId());
        assertEquals("Leite Desnatado", editado.getNome());
        assertEquals("Bebidas", editado.getCategoria());
        assertEquals("7891000100103", editado.getCodigoBarras());
    }

    @Test
    void editarComNomeVazio_deveMostrarMensagemENaoAlterar() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", "Laticínios");

        mockMvc.perform(post("/produtos/{id}/editar", leite.getId()).with(user(ANA)).with(csrf())
                        .param("nome", " ").param("categoria", "Outra"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("O nome do produto é obrigatório.")))
                .andExpect(content().string(containsString("value=\"Outra\"")));

        assertEquals("Leite", produtoService.buscarPorId(ANA.getId(), leite.getId()).getNome());
    }

    @Test
    void editarOuExcluirProdutoDeOutroUsuario_deveSerRecusado() throws Exception {
        Produto doBruno = produtoService.cadastrar(BRUNO.getId(), "111", "Café do Bruno", null);

        mockMvc.perform(get("/produtos/{id}/editar", doBruno.getId()).with(user(ANA)))
                .andExpect(redirectedUrl("/produtos"))
                .andExpect(flash().attribute("erro", "Produto não encontrado."));
        mockMvc.perform(post("/produtos/{id}/editar", doBruno.getId()).with(user(ANA)).with(csrf())
                        .param("nome", "Invadido"))
                .andExpect(redirectedUrl("/produtos"));
        mockMvc.perform(post("/produtos/{id}/excluir", doBruno.getId()).with(user(ANA)).with(csrf()))
                .andExpect(redirectedUrl("/produtos"))
                .andExpect(flash().attribute("erro", "Produto não encontrado."));

        assertEquals("Café do Bruno", produtoService.buscarPorId(BRUNO.getId(), doBruno.getId()).getNome());
    }

    @Test
    void excluirProdutoSemLotes_deveRemoverDaLista() throws Exception {
        Produto vazio = produtoService.cadastrar(ANA.getId(), "111", "Arroz", null);

        mockMvc.perform(post("/produtos/{id}/excluir", vazio.getId()).with(user(ANA)).with(csrf()))
                .andExpect(redirectedUrl("/produtos"))
                .andExpect(flash().attribute("sucesso", "Produto \"Arroz\" excluído."));

        assertTrue(produtoService.listar(ANA.getId()).isEmpty());
    }

    @Test
    void excluirProdutoComLotes_deveSerRecusadoMostrandoOMotivo() throws Exception {
        Produto leite = produtoService.cadastrar(ANA.getId(), "111", "Leite", null);
        Lote lote = lote(ANA, leite, 1, 4.0, HOJE.plusDays(5));
        loteService.consumir(ANA.getId(), leite.getId(), lote.getId());

        mockMvc.perform(post("/produtos/{id}/excluir", leite.getId()).with(user(ANA)).with(csrf()))
                .andExpect(redirectedUrl("/produtos"))
                .andExpect(flash().attribute("erro", containsString("não pode ser excluído porque tem 1 lote(s)")));

        assertEquals(1, produtoService.listar(ANA.getId()).size());
    }
}
