package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

class ConfirmacoesWebTest extends TesteWebBase {

    private Produto agua;
    private Lote lote;

    @BeforeEach
    void cadastrarAgua() {
        agua = produtoService.cadastrar(ANA.getId(), "7891000100103", "Água Mineral", null);
        lote = lote(ANA, agua, 4, 2.0, HOJE.plusDays(10));
    }

    @Test
    void consumir_devePerguntarQuantasDeQuantasUnidadesDeQualProduto() throws Exception {
        mockMvc.perform(get("/consumir").with(user(ANA)).param("produtoId", agua.getId()).param("quantidade", "1"))
                .andExpect(content().string(containsString("Consumir 1 de 4 unidades de Água Mineral?")))
                .andExpect(content().string(containsString("Sim, consumir 1")));
    }

    @Test
    void descartar_deveDizerQueAQuantidadeEscolhidaEntraNasPerdas() throws Exception {
        mockMvc.perform(get("/lotes/{p}/{l}/descartar", agua.getId(), lote.getId()).with(user(ANA)))
                .andExpect(content().string(containsString(
                        "Descartar {quantidade} de 4 unidades de Água Mineral? Isso entra no total de perdas.")));
    }

    @Test
    void desfazer_deveDizerQuantoVoltaParaADespensa() throws Exception {
        loteService.descartar(ANA.getId(), agua.getId(), lote.getId(), 1);

        mockMvc.perform(get("/historico").with(user(ANA)))
                .andExpect(content().string(containsString(
                        "Desfazer: devolver 1 unidade de Água Mineral para a despensa? O valor sai do total de perdas.")));
    }

    @Test
    void excluirProduto_deveIdentificarOProdutoEAvisarQueNaoTemVolta() throws Exception {
        mockMvc.perform(get("/produtos").with(user(ANA)))
                .andExpect(content().string(containsString(
                        "Excluir o produto &quot;Água Mineral&quot; (código 7891000100103)? Isso não pode ser desfeito.")));
    }
}
