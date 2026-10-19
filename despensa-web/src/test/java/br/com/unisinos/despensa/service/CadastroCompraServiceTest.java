package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.integracao.ConsultaProdutoExterna;
import br.com.unisinos.despensa.integracao.InfoProdutoExterno;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.RegistroCompra;
import br.com.unisinos.despensa.model.ResultadoConsultaCodigo;
import br.com.unisinos.despensa.model.ResultadoConsultaCodigo.Situacao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static br.com.unisinos.despensa.service.AmbienteDeTeste.HOJE;
import static br.com.unisinos.despensa.service.AmbienteDeTeste.OUTRO_USUARIO;
import static br.com.unisinos.despensa.service.AmbienteDeTeste.USUARIO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CadastroCompraServiceTest {

    private static final String CODIGO = "7891000100103";

    private final AmbienteDeTeste ambiente = new AmbienteDeTeste();
    private final ConsultaProdutoExterna consultaExterna = mock(ConsultaProdutoExterna.class);
    private final CadastroCompraService cadastroCompraService =
            new CadastroCompraService(ambiente.produtoService, ambiente.loteService, consultaExterna);

    @AfterEach
    void limparInterrupcao() {
        Thread.interrupted();
    }

    @Test
    void consultarCodigoDeProdutoJaCadastrado_deveTrazerONomeSemConsultarAOpenFoodFacts() {
        ambiente.produtoService.cadastrar(USUARIO, CODIGO, "Leite Integral", "Laticínios");

        ResultadoConsultaCodigo resultado = cadastroCompraService.consultarCodigo(USUARIO, " " + CODIGO + " ");

        assertEquals(Situacao.EXISTENTE, resultado.situacao());
        assertEquals("Leite Integral", resultado.nome());
        verifyNoInteractions(consultaExterna);
    }

    @Test
    void consultarCodigoNovoEncontradoNaOpenFoodFacts_deveTrazerNomeECategoria() throws Exception {
        when(consultaExterna.buscarPorCodigoBarras(CODIGO))
                .thenReturn(Optional.of(new InfoProdutoExterno("Leite Condensado", "Laticínios")));

        ResultadoConsultaCodigo resultado = cadastroCompraService.consultarCodigo(USUARIO, CODIGO);

        assertEquals(Situacao.ENCONTRADO, resultado.situacao());
        assertEquals("Leite Condensado", resultado.nome());
        assertEquals("Laticínios", resultado.categoria());
    }

    @Test
    void consultarCodigoNaoEncontradoNaOpenFoodFacts_deveAvisarParaPreencherAMao() throws Exception {
        when(consultaExterna.buscarPorCodigoBarras(CODIGO)).thenReturn(Optional.empty());

        ResultadoConsultaCodigo resultado = cadastroCompraService.consultarCodigo(USUARIO, CODIGO);

        assertEquals(Situacao.NAO_ENCONTRADO, resultado.situacao());
        assertTrue(resultado.mensagem().contains("não encontrado"));
        assertNull(resultado.nome());
    }

    @Test
    void consultarComFalhaNaOpenFoodFacts_deveAvisarDaFalhaSemLancarExcecao() throws Exception {
        when(consultaExterna.buscarPorCodigoBarras(CODIGO)).thenThrow(new HttpTimeoutException("timeout"));

        ResultadoConsultaCodigo resultado = cadastroCompraService.consultarCodigo(USUARIO, CODIGO);

        assertEquals(Situacao.FALHA, resultado.situacao());
        assertTrue(resultado.mensagem().contains("Não foi possível consultar"));
    }

    @Test
    void consultaInterrompida_deveAvisarDaFalhaEManterOSinalDeInterrupcao() throws Exception {
        when(consultaExterna.buscarPorCodigoBarras(CODIGO)).thenThrow(new InterruptedException());

        ResultadoConsultaCodigo resultado = cadastroCompraService.consultarCodigo(USUARIO, CODIGO);

        assertEquals(Situacao.FALHA, resultado.situacao());
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void consultarCodigoVazio_deveAvisarSemConsultarNada() {
        assertEquals(Situacao.CODIGO_VAZIO, cadastroCompraService.consultarCodigo(USUARIO, "  ").situacao());
        verifyNoInteractions(consultaExterna);
    }

    @Test
    void consultarCodigoForaDoPadrao_naoDeveConsultarAOpenFoodFacts() {
        assertEquals(Situacao.SEM_CONSULTA, cadastroCompraService.consultarCodigo(USUARIO, "FEIRA-01").situacao());
        verifyNoInteractions(consultaExterna);
    }

    @Test
    void consultarCodigoQueSoOutroUsuarioCadastrou_deveConsultarAOpenFoodFacts() throws Exception {
        ambiente.produtoService.cadastrar(OUTRO_USUARIO, CODIGO, "Leite do Outro", null);
        when(consultaExterna.buscarPorCodigoBarras(anyString())).thenReturn(Optional.empty());

        ResultadoConsultaCodigo resultado = cadastroCompraService.consultarCodigo(USUARIO, CODIGO);

        assertEquals(Situacao.NAO_ENCONTRADO, resultado.situacao());
        verify(consultaExterna).buscarPorCodigoBarras(CODIGO);
    }

    @Test
    void registrarCompraDeProdutoNovo_deveGravarProdutoELoteNumaUnicaOperacao() {
        int gravacoesAntes = ambiente.repositorio.getGravacoes();

        RegistroCompra registro = cadastroCompraService.registrarCompra(USUARIO, CODIGO, "Leite", "Laticínios",
                Map.of(HOJE.plusDays(10), 3), 4.5);

        assertTrue(registro.produtoNovo());
        assertEquals(1, registro.lotesCadastrados());
        assertEquals(gravacoesAntes + 1, ambiente.repositorio.getGravacoes());
        Produto salvo = ambiente.produtoService.buscarPorCodigoBarras(USUARIO, CODIGO).orElseThrow();
        assertEquals("Leite", salvo.getNome());
        assertEquals(1, salvo.getLotes().size());
        assertEquals(3, salvo.getLotes().get(0).getQuantidade());
        assertEquals(4.5, salvo.getLotes().get(0).getPrecoUnitario());
    }

    @Test
    void registrarCompraDeProdutoExistente_deveSoAcrescentarOLoteSemMudarOProduto() {
        Produto existente = ambiente.produtoService.cadastrar(USUARIO, CODIGO, "Leite Integral", "Laticínios");

        RegistroCompra registro = cadastroCompraService.registrarCompra(USUARIO, CODIGO, "Outro Nome", "Outra",
                Map.of(HOJE.plusDays(5), 2), 3.0);

        assertFalse(registro.produtoNovo());
        Produto depois = ambiente.recarregar(USUARIO, existente);
        assertEquals("Leite Integral", depois.getNome());
        assertEquals("Laticínios", depois.getCategoria());
        assertEquals(1, depois.getLotes().size());
        assertEquals(1, ambiente.produtoService.listar(USUARIO).size());
    }

    @Test
    void registrarCompraComValidadesDiferentes_deveCriarUmLotePorData() {
        Map<LocalDate, Integer> quantidadePorValidade = new LinkedHashMap<>();
        quantidadePorValidade.put(HOJE.plusDays(20), 2);
        quantidadePorValidade.put(HOJE.plusDays(3), 1);

        RegistroCompra registro = cadastroCompraService.registrarCompra(USUARIO, CODIGO, "Iogurte", null,
                quantidadePorValidade, 2.0);

        assertEquals(2, registro.lotesCadastrados());
        List<Lote> lotes = ambiente.lotes(USUARIO, registro.produto());
        assertEquals(2, lotes.size());
    }

    @Test
    void registrarCompraDeProdutoNovoComLoteInvalido_naoDeveCriarOProduto() {
        assertThrows(ValidacaoException.class, () -> cadastroCompraService.registrarCompra(USUARIO, CODIGO, "Leite",
                null, Map.of(HOJE.minusDays(1), 1), 4.5));

        assertTrue(ambiente.produtoService.listar(USUARIO).isEmpty());
    }

    @Test
    void registrarCompraDeProdutoNovoSemNome_deveSerRecusada() {
        assertThrows(ValidacaoException.class, () -> cadastroCompraService.registrarCompra(USUARIO, CODIGO, " ",
                null, Map.of(HOJE.plusDays(5), 1), 4.5));

        assertTrue(ambiente.produtoService.listar(USUARIO).isEmpty());
    }

    @Test
    void registrarCompraComPrecoNegativo_deveSerRecusada() {
        assertThrows(ValidacaoException.class, () -> cadastroCompraService.registrarCompra(USUARIO, CODIGO, "Leite",
                null, Map.of(HOJE.plusDays(5), 1), -1.0));
    }

    @Test
    void falhaDeRedeTambemEhTratadaComoFalha() throws Exception {
        when(consultaExterna.buscarPorCodigoBarras(CODIGO)).thenThrow(new IOException("sem internet"));

        assertEquals(Situacao.FALHA, cadastroCompraService.consultarCodigo(USUARIO, CODIGO).situacao());
    }
}
