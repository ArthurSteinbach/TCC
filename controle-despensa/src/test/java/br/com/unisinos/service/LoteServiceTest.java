package br.com.unisinos.service;

import br.com.unisinos.dao.LoteDAO;
import br.com.unisinos.db.DatabaseManager;
import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.ItemConsumo;
import br.com.unisinos.model.Lote;
import br.com.unisinos.model.Produto;
import br.com.unisinos.model.StatusLote;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes da camada de negócio de Lote: validações de cadastro, a
 * ordenação FEFO, o filtro de alerta de vencimento, as baixas
 * (consumido/descartado) e o cálculo de perda financeira.
 */
class LoteServiceTest {

    private static LoteService loteService;
    private static ProdutoService produtoService;

    @TempDir
    static Path pastaTemporaria;

    private Produto produtoDeTeste;

    @BeforeAll
    static void configurarBancoDeTeste() {
        String caminhoBanco = pastaTemporaria.resolve("despensa_teste.db").toAbsolutePath().toString();
        System.setProperty("despensa.db.path", caminhoBanco);
        DatabaseManager.inicializar();
        loteService = new LoteService();
        produtoService = new ProdutoService();
    }

    /** Cada teste cadastra o seu próprio produto, com código único, para não interferir nos demais. */
    @BeforeEach
    void cadastrarProdutoDeTeste() throws SQLException {
        String codigo = "TESTE-" + System.nanoTime();
        produtoDeTeste = produtoService.cadastrar(codigo, "Produto de Teste", "Categoria Teste");
    }

    @Test
    void cadastrarComDadosValidos_devePersistirComStatusAtivo() throws SQLException {
        Lote lote = loteService.cadastrar(produtoDeTeste.getId(), 2, 10.0, LocalDate.now().plusDays(10));

        assertNotNull(lote.getId());
        assertEquals(StatusLote.ATIVO, lote.getStatus());
    }

    @Test
    void cadastrarComValidadeNoPassado_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().minusDays(1)));
    }

    @Test
    void cadastrarComQuantidadeZero_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrar(produtoDeTeste.getId(), 0, 10.0, LocalDate.now().plusDays(5)));
    }

    @Test
    void cadastrarComPrecoNegativo_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrar(produtoDeTeste.getId(), 1, -5.0, LocalDate.now().plusDays(5)));
    }

    /**
     * Teste central do sistema: prova que, independente da ordem de
     * cadastro, a listagem sempre devolve os lotes ordenados pela regra
     * FEFO (validade mais próxima primeiro).
     */
    @Test
    void listarAtivosFEFO_deveOrdenarPorValidadeMaisProximaPrimeiro() throws SQLException {
        loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().plusDays(30));
        loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().plusDays(3));
        loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().plusDays(10));

        List<Lote> lotesDoProduto = loteService.listarAtivosFEFO().stream()
                .filter(l -> l.getProdutoId().equals(produtoDeTeste.getId()))
                .toList();

        assertEquals(3, lotesDoProduto.size());
        assertEquals(LocalDate.now().plusDays(3), lotesDoProduto.get(0).getDataValidade());
        assertEquals(LocalDate.now().plusDays(10), lotesDoProduto.get(1).getDataValidade());
        assertEquals(LocalDate.now().plusDays(30), lotesDoProduto.get(2).getDataValidade());
    }

    @Test
    void listarAlertaVencimento_deveIncluirSoItensDentroDaJanela() throws SQLException {
        loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().plusDays(3));  // dentro (padrão 7 dias)
        loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().plusDays(30)); // fora

        List<Lote> emAlerta = loteService.listarAlertaVencimento().stream()
                .filter(l -> l.getProdutoId().equals(produtoDeTeste.getId()))
                .toList();

        assertEquals(1, emAlerta.size());
        assertEquals(LocalDate.now().plusDays(3), emAlerta.get(0).getDataValidade());
    }

    @Test
    void descartar_deveSomarValorNaPerdaFinanceiraTotal() throws SQLException {
        double perdaAntes = loteService.calcularPerdaFinanceiraTotal();

        Lote lote = loteService.cadastrar(produtoDeTeste.getId(), 3, 12.50, LocalDate.now().plusDays(5));
        loteService.descartar(lote.getId());

        double perdaDepois = loteService.calcularPerdaFinanceiraTotal();

        assertEquals(perdaAntes + 37.50, perdaDepois, 0.001,
                "3 unidades a R$ 12,50 descartadas devem somar R$ 37,50 na perda total.");
    }

    @Test
    void consumir_naoDeveAfetarPerdaFinanceira() throws SQLException {
        double perdaAntes = loteService.calcularPerdaFinanceiraTotal();

        Lote lote = loteService.cadastrar(produtoDeTeste.getId(), 5, 100.0, LocalDate.now().plusDays(5));
        loteService.consumir(lote.getId());

        double perdaDepois = loteService.calcularPerdaFinanceiraTotal();

        assertEquals(perdaAntes, perdaDepois, 0.001,
                "Consumir um item não é desperdício, então não deve alterar a perda financeira.");
    }

    @Test
    void editar_deveAtualizarQuantidadePrecoEValidade() throws SQLException {
        Lote lote = loteService.cadastrar(produtoDeTeste.getId(), 1, 10.0, LocalDate.now().plusDays(5));

        LocalDate novaValidade = LocalDate.now().plusDays(20);
        loteService.editar(lote.getId(), 4, 25.0, novaValidade);

        Lote atualizado = loteService.listarAtivosFEFO().stream()
                .filter(l -> l.getId().equals(lote.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(4, atualizado.getQuantidade());
        assertEquals(25.0, atualizado.getPrecoUnitario());
        assertEquals(novaValidade, atualizado.getDataValidade());
    }

    @Test
    void cadastrarPorValidade_deveCriarUmLotePorDataComAQuantidadeDeCadaUma() throws SQLException {
        Map<LocalDate, Integer> quantidadePorValidade = new LinkedHashMap<>();
        quantidadePorValidade.put(LocalDate.now().plusDays(20), 3);
        quantidadePorValidade.put(LocalDate.now().plusDays(5), 2);

        List<Lote> cadastrados = loteService.cadastrarPorValidade(produtoDeTeste.getId(), quantidadePorValidade, 8.0);

        List<Lote> lotesDoProduto = lotesAtivosDoProdutoDeTeste();

        assertEquals(2, cadastrados.size());
        assertEquals(2, lotesDoProduto.size());
        assertEquals(LocalDate.now().plusDays(5), lotesDoProduto.get(0).getDataValidade());
        assertEquals(2, lotesDoProduto.get(0).getQuantidade());
        assertEquals(LocalDate.now().plusDays(20), lotesDoProduto.get(1).getDataValidade());
        assertEquals(3, lotesDoProduto.get(1).getQuantidade());
    }

    @Test
    void cadastrarPorValidade_deveAplicarOMesmoPrecoATodosOsLotes() throws SQLException {
        Map<LocalDate, Integer> quantidadePorValidade = new LinkedHashMap<>();
        quantidadePorValidade.put(LocalDate.now().plusDays(3), 1);
        quantidadePorValidade.put(LocalDate.now().plusDays(9), 1);
        quantidadePorValidade.put(LocalDate.now().plusDays(15), 4);

        loteService.cadastrarPorValidade(produtoDeTeste.getId(), quantidadePorValidade, 6.75);

        List<Lote> lotesDoProduto = lotesAtivosDoProdutoDeTeste();

        assertEquals(3, lotesDoProduto.size());
        assertTrue(lotesDoProduto.stream().allMatch(l -> l.getPrecoUnitario() == 6.75));
    }

    @Test
    void cadastrarPorValidade_comUmaDataVencida_naoDeveSalvarNenhumLote() throws SQLException {
        Map<LocalDate, Integer> quantidadePorValidade = new LinkedHashMap<>();
        quantidadePorValidade.put(LocalDate.now().plusDays(10), 2);
        quantidadePorValidade.put(LocalDate.now().minusDays(1), 1);

        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrarPorValidade(produtoDeTeste.getId(), quantidadePorValidade, 5.0));

        assertTrue(lotesAtivosDoProdutoDeTeste().isEmpty());
    }

    @Test
    void cadastrarPorValidade_semNenhumaValidade_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrarPorValidade(produtoDeTeste.getId(), new LinkedHashMap<>(), 5.0));
    }

    @Test
    void agruparPorValidade_deveSomarAsUnidadesComAMesmaValidade() {
        LocalDate validadeA = LocalDate.now().plusDays(4);
        LocalDate validadeB = LocalDate.now().plusDays(12);

        Map<LocalDate, Integer> agrupado = LoteService.agruparPorValidade(
                List.of(validadeA, validadeB, validadeA, validadeA));

        assertEquals(2, agrupado.size());
        assertEquals(3, agrupado.get(validadeA));
        assertEquals(1, agrupado.get(validadeB));
    }

    @Test
    void agruparPorValidade_comTodasAsValidadesIguais_deveGerarUmUnicoGrupo() {
        LocalDate validade = LocalDate.now().plusDays(7);

        Map<LocalDate, Integer> agrupado = LoteService.agruparPorValidade(List.of(validade, validade, validade));

        assertEquals(Map.of(validade, 3), agrupado);
    }

    @Test
    void agruparPorValidade_comTodasAsValidadesDiferentes_deveGerarUmGrupoPorItem() {
        LocalDate validadeA = LocalDate.now().plusDays(2);
        LocalDate validadeB = LocalDate.now().plusDays(8);
        LocalDate validadeC = LocalDate.now().plusDays(30);

        Map<LocalDate, Integer> agrupado = LoteService.agruparPorValidade(List.of(validadeC, validadeA, validadeB));

        assertEquals(List.of(validadeC, validadeA, validadeB), List.copyOf(agrupado.keySet()));
        assertTrue(agrupado.values().stream().allMatch(quantidade -> quantidade == 1));
    }

    @Test
    void agruparPorValidade_semItens_deveRetornarMapaVazio() {
        assertTrue(LoteService.agruparPorValidade(List.of()).isEmpty());
    }

    @Test
    void agruparECadastrarPorValidade_deveSalvarUmLotePorDataComAQuantidadeSomada() throws SQLException {
        LocalDate validadeProxima = LocalDate.now().plusDays(3);
        LocalDate validadeDistante = LocalDate.now().plusDays(25);

        Map<LocalDate, Integer> agrupado = LoteService.agruparPorValidade(
                List.of(validadeDistante, validadeProxima, validadeDistante, validadeProxima, validadeDistante));
        loteService.cadastrarPorValidade(produtoDeTeste.getId(), agrupado, 4.5);

        List<Lote> lotesDoProduto = lotesAtivosDoProdutoDeTeste();

        assertEquals(2, lotesDoProduto.size());
        assertEquals(validadeProxima, lotesDoProduto.get(0).getDataValidade());
        assertEquals(2, lotesDoProduto.get(0).getQuantidade());
        assertEquals(validadeDistante, lotesDoProduto.get(1).getDataValidade());
        assertEquals(3, lotesDoProduto.get(1).getQuantidade());
        assertTrue(lotesDoProduto.stream().allMatch(l -> l.getPrecoUnitario() == 4.5));
    }

    @Test
    void consumirPorFEFO_dentroDeUmLote_deveManterORestanteAtivoECriarRegistroConsumido() throws SQLException {
        LocalDate validade = LocalDate.now().plusDays(10);
        Lote lote = loteService.cadastrar(produtoDeTeste.getId(), 5, 4.0, validade);

        loteService.consumirPorFEFO(produtoDeTeste.getId(), 2);

        List<Lote> todos = todosOsLotesDoProdutoDeTeste();
        assertEquals(2, todos.size());

        Lote original = loteComId(todos, lote.getId());
        assertEquals(StatusLote.ATIVO, original.getStatus());
        assertEquals(3, original.getQuantidade());
        assertNull(original.getDataBaixa());

        Lote consumido = todos.stream().filter(l -> !l.getId().equals(lote.getId())).findFirst().orElseThrow();
        assertEquals(StatusLote.CONSUMIDO, consumido.getStatus());
        assertEquals(2, consumido.getQuantidade());
        assertEquals(validade, consumido.getDataValidade());
        assertEquals(4.0, consumido.getPrecoUnitario());
        assertNotNull(consumido.getDataBaixa());
    }

    @Test
    void consumirPorFEFO_passandoPorDoisLotes_deveConsumirPrimeiroOQueVenceAntes() throws SQLException {
        Lote distante = loteService.cadastrar(produtoDeTeste.getId(), 4, 6.0, LocalDate.now().plusDays(20));
        Lote proximo = loteService.cadastrar(produtoDeTeste.getId(), 3, 5.0, LocalDate.now().plusDays(2));

        List<ItemConsumo> plano = loteService.consumirPorFEFO(produtoDeTeste.getId(), 5);

        assertEquals(2, plano.size());
        assertEquals(proximo.getId(), plano.get(0).lote().getId());
        assertEquals(3, plano.get(0).quantidadeConsumida());
        assertEquals(distante.getId(), plano.get(1).lote().getId());
        assertEquals(2, plano.get(1).quantidadeConsumida());

        List<Lote> todos = todosOsLotesDoProdutoDeTeste();
        assertEquals(StatusLote.CONSUMIDO, loteComId(todos, proximo.getId()).getStatus());
        assertEquals(3, loteComId(todos, proximo.getId()).getQuantidade());

        Lote restanteDistante = loteComId(todos, distante.getId());
        assertEquals(StatusLote.ATIVO, restanteDistante.getStatus());
        assertEquals(2, restanteDistante.getQuantidade());

        List<Lote> novosConsumidos = todos.stream()
                .filter(l -> !l.getId().equals(proximo.getId()) && !l.getId().equals(distante.getId()))
                .toList();
        assertEquals(1, novosConsumidos.size());
        assertEquals(StatusLote.CONSUMIDO, novosConsumidos.get(0).getStatus());
        assertEquals(2, novosConsumidos.get(0).getQuantidade());
        assertEquals(LocalDate.now().plusDays(20), novosConsumidos.get(0).getDataValidade());
        assertEquals(6.0, novosConsumidos.get(0).getPrecoUnitario());
    }

    @Test
    void consumirPorFEFO_doLoteInteiro_deveSoMudarStatusParaConsumido() throws SQLException {
        Lote lote = loteService.cadastrar(produtoDeTeste.getId(), 3, 7.0, LocalDate.now().plusDays(5));

        loteService.consumirPorFEFO(produtoDeTeste.getId(), 3);

        List<Lote> todos = todosOsLotesDoProdutoDeTeste();
        assertEquals(1, todos.size());
        assertEquals(lote.getId(), todos.get(0).getId());
        assertEquals(StatusLote.CONSUMIDO, todos.get(0).getStatus());
        assertEquals(3, todos.get(0).getQuantidade());
        assertNotNull(todos.get(0).getDataBaixa());
    }

    @Test
    void consumirPorFEFO_deveIgnorarLotesVencidos() throws SQLException {
        Lote vencido = loteService.cadastrar(produtoDeTeste.getId(), 2, 3.0, LocalDate.now().plusDays(1));
        loteService.editar(vencido.getId(), 2, 3.0, LocalDate.now().minusDays(2));
        Lote valido = loteService.cadastrar(produtoDeTeste.getId(), 4, 3.0, LocalDate.now().plusDays(8));

        List<ItemConsumo> plano = loteService.consumirPorFEFO(produtoDeTeste.getId(), 1);

        assertEquals(1, plano.size());
        assertEquals(valido.getId(), plano.get(0).lote().getId());

        List<Lote> todos = todosOsLotesDoProdutoDeTeste();
        Lote vencidoDepois = loteComId(todos, vencido.getId());
        assertEquals(StatusLote.ATIVO, vencidoDepois.getStatus());
        assertEquals(2, vencidoDepois.getQuantidade());
        assertEquals(3, loteComId(todos, valido.getId()).getQuantidade());
    }

    @Test
    void consumirPorFEFO_comQuantidadeMaiorQueODisponivel_naoDeveAlterarNada() throws SQLException {
        loteService.cadastrar(produtoDeTeste.getId(), 2, 5.0, LocalDate.now().plusDays(3));
        loteService.cadastrar(produtoDeTeste.getId(), 1, 5.0, LocalDate.now().plusDays(9));
        Lote vencido = loteService.cadastrar(produtoDeTeste.getId(), 10, 5.0, LocalDate.now().plusDays(1));
        loteService.editar(vencido.getId(), 10, 5.0, LocalDate.now().minusDays(1));

        List<String> antes = resumoDosLotesDoProdutoDeTeste();

        ValidacaoException erro = assertThrows(ValidacaoException.class, () ->
                loteService.consumirPorFEFO(produtoDeTeste.getId(), 4));

        assertTrue(erro.getMessage().contains("3"));
        assertEquals(antes, resumoDosLotesDoProdutoDeTeste());
    }

    @Test
    void consumirPorFEFO_comProdutoSoComLotesVencidos_deveSugerirODescarte() throws SQLException {
        Lote vencido = loteService.cadastrar(produtoDeTeste.getId(), 2, 5.0, LocalDate.now().plusDays(1));
        loteService.editar(vencido.getId(), 2, 5.0, LocalDate.now().minusDays(3));

        List<String> antes = resumoDosLotesDoProdutoDeTeste();

        ValidacaoException erro = assertThrows(ValidacaoException.class, () ->
                loteService.consumirPorFEFO(produtoDeTeste.getId(), 1));

        assertTrue(erro.getMessage().contains("vencidos"));
        assertTrue(erro.getMessage().contains("descartados"));
        assertEquals(antes, resumoDosLotesDoProdutoDeTeste());
    }

    private List<Lote> todosOsLotesDoProdutoDeTeste() throws SQLException {
        return new LoteDAO().listarPorProduto(produtoDeTeste.getId());
    }

    private List<String> resumoDosLotesDoProdutoDeTeste() throws SQLException {
        return todosOsLotesDoProdutoDeTeste().stream()
                .map(l -> l.getId() + "|" + l.getStatus() + "|" + l.getQuantidade() + "|" + l.getDataBaixa())
                .toList();
    }

    private static Lote loteComId(List<Lote> lotes, Integer id) {
        return lotes.stream().filter(l -> l.getId().equals(id)).findFirst().orElseThrow();
    }

    private List<Lote> lotesAtivosDoProdutoDeTeste() throws SQLException {
        return loteService.listarAtivosFEFO().stream()
                .filter(l -> l.getProdutoId().equals(produtoDeTeste.getId()))
                .toList();
    }

}
