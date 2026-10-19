package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.integracao.InfoProdutoExterno;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CadastroWebTest extends TesteWebBase {

    private static final String CODIGO = "7891000100103";

    @Test
    void paginaCadastrar_deveTerOLeitorDeCodigoADigitacaoManualEOSeletorDeData() throws Exception {
        mockMvc.perform(get("/cadastrar").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ler código de barras")))
                .andExpect(content().string(containsString("id=\"codigoBarras\"")))
                .andExpect(content().string(containsString("type=\"date\"")))
                .andExpect(content().string(containsString("min=\"2026-10-18\"")))
                .andExpect(content().string(containsString("Todos os itens têm a mesma validade")))
                .andExpect(content().string(containsString("@zxing/browser@0.2.1/umd/zxing-browser.min.js")))
                .andExpect(content().string(containsString("integrity=\"sha384-")))
                .andExpect(content().string(containsString("/js/leitor-codigo-barras.js")));
    }

    @Test
    void consulta_deProdutoJaCadastradoDoUsuario_deveDevolverONome() throws Exception {
        produtoService.cadastrar(ANA.getId(), CODIGO, "Leite Integral", "Laticínios");

        mockMvc.perform(get("/cadastrar/consulta").param("codigo", CODIGO).with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.situacao").value("EXISTENTE"))
                .andExpect(jsonPath("$.nome").value("Leite Integral"));

        verifyNoInteractions(consultaProdutoExterna);
    }

    @Test
    void consulta_deProdutoNovoEncontradoNaOpenFoodFacts_deveDevolverNomeECategoria() throws Exception {
        when(consultaProdutoExterna.buscarPorCodigoBarras(CODIGO))
                .thenReturn(Optional.of(new InfoProdutoExterno("Leite Condensado", "Laticínios")));

        mockMvc.perform(get("/cadastrar/consulta").param("codigo", CODIGO).with(user(ANA)))
                .andExpect(jsonPath("$.situacao").value("ENCONTRADO"))
                .andExpect(jsonPath("$.nome").value("Leite Condensado"))
                .andExpect(jsonPath("$.categoria").value("Laticínios"));
    }

    @Test
    void consulta_deProdutoNaoEncontradoNaOpenFoodFacts_deveAvisarParaPreencherAMao() throws Exception {
        when(consultaProdutoExterna.buscarPorCodigoBarras(CODIGO)).thenReturn(Optional.empty());

        mockMvc.perform(get("/cadastrar/consulta").param("codigo", CODIGO).with(user(ANA)))
                .andExpect(jsonPath("$.situacao").value("NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensagem").value(containsString("Preencha o nome e a categoria")))
                .andExpect(jsonPath("$.nome").value(nullValue()));
    }

    @Test
    void consulta_comFalhaNaOpenFoodFacts_deveAvisarDaFalha() throws Exception {
        when(consultaProdutoExterna.buscarPorCodigoBarras(CODIGO)).thenThrow(new IOException("sem internet"));

        mockMvc.perform(get("/cadastrar/consulta").param("codigo", CODIGO).with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("FALHA"))
                .andExpect(jsonPath("$.mensagem").value(containsString("Não foi possível consultar a Open Food Facts")));
    }

    @Test
    void consulta_naoDeveEnxergarProdutoDeOutroUsuario() throws Exception {
        produtoService.cadastrar(BRUNO.getId(), CODIGO, "Leite do Bruno", null);
        when(consultaProdutoExterna.buscarPorCodigoBarras(CODIGO)).thenReturn(Optional.empty());

        mockMvc.perform(get("/cadastrar/consulta").param("codigo", CODIGO).with(user(ANA)))
                .andExpect(jsonPath("$.situacao").value("NAO_ENCONTRADO"));
    }

    @Test
    void salvarProdutoNovo_deveCadastrarProdutoELoteEVoltarParaADespensa() throws Exception {
        mockMvc.perform(salvar().param("nome", "Leite Condensado").param("categoria", "Laticínios")
                        .param("quantidade", "3").param("precoUnitario", "6,49")
                        .param("mesmaValidade", "true").param("validade", "2026-12-01"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", "Produto \"Leite Condensado\" cadastrado com 1 lote."));

        Produto salvo = produtoService.buscarPorCodigoBarras(ANA.getId(), CODIGO).orElseThrow();
        assertEquals("Leite Condensado", salvo.getNome());
        assertEquals("Laticínios", salvo.getCategoria());
        Lote lote = salvo.getLotes().get(0);
        assertEquals(3, lote.getQuantidade());
        assertEquals(6.49, lote.getPrecoUnitario(), 0.001);
        assertEquals(LocalDate.of(2026, 12, 1), lote.getDataValidade());
    }

    @Test
    void salvarProdutoJaExistente_deveSoAcrescentarOLote() throws Exception {
        Produto existente = produtoService.cadastrar(ANA.getId(), CODIGO, "Leite Integral", "Laticínios");

        mockMvc.perform(salvar().param("quantidade", "2").param("precoUnitario", "5.00")
                        .param("mesmaValidade", "true").param("validade", "2026-11-10"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", "Lote de \"Leite Integral\" cadastrado com sucesso."));

        Produto depois = produtoService.buscarPorId(ANA.getId(), existente.getId());
        assertEquals("Leite Integral", depois.getNome());
        assertEquals(1, depois.getLotes().size());
        assertEquals(1, produtoService.listar(ANA.getId()).size());
    }

    @Test
    void salvarComValidadesDiferentes_deveCriarUmLotePorDataUsandoOCadastroPorValidades() throws Exception {
        mockMvc.perform(salvar().param("nome", "Iogurte").param("quantidade", "3").param("precoUnitario", "2,50")
                        .param("validades", "2026-11-01", "2026-10-25", "2026-11-01"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso",
                        "Produto \"Iogurte\" cadastrado com 2 lotes (agrupados por validade)."));

        List<Lote> lotes = produtoService.buscarPorCodigoBarras(ANA.getId(), CODIGO).orElseThrow().getLotes();
        assertEquals(2, lotes.size());
        assertEquals(2, quantidadeComValidade(lotes, LocalDate.of(2026, 11, 1)));
        assertEquals(1, quantidadeComValidade(lotes, LocalDate.of(2026, 10, 25)));
    }

    @Test
    void salvarComUmaUnidadeSemValidade_deveMostrarQualUnidadeFaltaENaoSalvar() throws Exception {
        mockMvc.perform(salvar().param("nome", "Iogurte").param("quantidade", "3").param("precoUnitario", "2,50")
                        .param("validades", "2026-11-01", "", "2026-11-03"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("a unidade 2 está sem data")))
                .andExpect(content().string(containsString("value=\"2026-11-03\"")));

        assertTrue(produtoService.listar(ANA.getId()).isEmpty());
    }

    @Test
    void salvarComQuantidadeInvalida_deveMostrarMensagemEManterOsDados() throws Exception {
        mockMvc.perform(salvar().param("nome", "Leite").param("quantidade", "0").param("precoUnitario", "4,50")
                        .param("mesmaValidade", "true").param("validade", "2026-12-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe a quantidade (um número inteiro maior que zero).")))
                .andExpect(content().string(containsString("value=\"Leite\"")))
                .andExpect(content().string(containsString("value=\"4,50\"")));

        assertTrue(produtoService.listar(ANA.getId()).isEmpty());
    }

    @Test
    void salvarComPrecoInvalido_deveMostrarMensagem() throws Exception {
        mockMvc.perform(salvar().param("nome", "Leite").param("quantidade", "1").param("precoUnitario", "abc")
                        .param("mesmaValidade", "true").param("validade", "2026-12-01"))
                .andExpect(content().string(containsString("Informe o preço unitário (ex: 4,99).")));

        assertTrue(produtoService.listar(ANA.getId()).isEmpty());
    }

    @Test
    void salvarSemValidade_deveMostrarMensagem() throws Exception {
        mockMvc.perform(salvar().param("nome", "Leite").param("quantidade", "1").param("precoUnitario", "4")
                        .param("mesmaValidade", "true").param("validade", ""))
                .andExpect(content().string(containsString("Informe a data de validade.")));

        assertTrue(produtoService.listar(ANA.getId()).isEmpty());
    }

    @Test
    void salvarComValidadeVencida_deveMostrarMensagemENaoCriarOProduto() throws Exception {
        mockMvc.perform(salvar().param("nome", "Leite").param("quantidade", "1").param("precoUnitario", "4")
                        .param("mesmaValidade", "true").param("validade", "2026-10-17"))
                .andExpect(content().string(containsString("Não é possível cadastrar um lote já vencido")));

        assertTrue(produtoService.listar(ANA.getId()).isEmpty());
    }

    @Test
    void salvarProdutoNovoSemNome_deveMostrarMensagem() throws Exception {
        mockMvc.perform(salvar().param("nome", " ").param("quantidade", "1").param("precoUnitario", "4")
                        .param("mesmaValidade", "true").param("validade", "2026-12-01"))
                .andExpect(content().string(containsString("O nome do produto é obrigatório.")));

        assertTrue(produtoService.listar(ANA.getId()).isEmpty());
    }

    @Test
    void salvarSemCodigoDeBarras_deveMostrarMensagem() throws Exception {
        mockMvc.perform(post("/cadastrar").with(user(ANA)).with(csrf()).param("codigoBarras", " ")
                        .param("nome", "Leite").param("quantidade", "1").param("precoUnitario", "4")
                        .param("mesmaValidade", "true").param("validade", "2026-12-01"))
                .andExpect(content().string(containsString("Informe o código de barras.")));
    }

    @Test
    void erroAoSalvarProdutoExistente_deveContinuarMostrandoOProdutoJaCadastrado() throws Exception {
        produtoService.cadastrar(ANA.getId(), CODIGO, "Leite Integral", "Laticínios");

        mockMvc.perform(salvar().param("quantidade", "1").param("precoUnitario", "")
                        .param("mesmaValidade", "true").param("validade", "2026-12-01"))
                .andExpect(content().string(containsString("Produto já cadastrado:")))
                .andExpect(content().string(containsString("Leite Integral")))
                .andExpect(content().string(not(containsString("id=\"secaoLote\" hidden"))));
    }

    @Test
    void salvarSemTokenCsrf_deveSerRecusado() throws Exception {
        mockMvc.perform(post("/cadastrar").with(user(ANA)).param("codigoBarras", CODIGO))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder salvar() {
        return post("/cadastrar").with(user(ANA)).with(csrf())
                .param("codigoBarras", CODIGO)
                .param("_mesmaValidade", "on");
    }

    private static double quantidadeComValidade(List<Lote> lotes, LocalDate validade) {
        return lotes.stream().filter(l -> l.getDataValidade().equals(validade)).findFirst().orElseThrow()
                .getQuantidade();
    }
}
