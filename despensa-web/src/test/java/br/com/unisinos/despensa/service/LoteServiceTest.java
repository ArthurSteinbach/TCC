package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.ItemConsumo;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.LoteDoProduto;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.ResumoDesperdicio;
import br.com.unisinos.despensa.model.StatusLote;
import br.com.unisinos.despensa.repository.ProdutoRepositoryEmMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static br.com.unisinos.despensa.service.AmbienteDeTeste.AGORA;
import static br.com.unisinos.despensa.service.AmbienteDeTeste.HOJE;
import static br.com.unisinos.despensa.service.AmbienteDeTeste.USUARIO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoteServiceTest {

    private final AmbienteDeTeste ambiente = new AmbienteDeTeste();
    private final LoteService loteService = ambiente.loteService;
    private final ProdutoRepositoryEmMemoria repositorio = ambiente.repositorio;

    private Produto produtoDeTeste;

    @BeforeEach
    void cadastrarProdutoDeTeste() {
        produtoDeTeste = ambiente.novoProduto(USUARIO);
    }

    @Test
    void cadastrarComDadosValidos_devePersistirComStatusAtivo() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 2, 10.0, HOJE.plusDays(10));

        assertNotNull(lote.getId());
        Lote salvo = ambiente.lote(USUARIO, produtoDeTeste, lote.getId());
        assertEquals(StatusLote.ATIVO, salvo.getStatus());
        assertEquals(2, salvo.getQuantidade());
        assertEquals(AGORA, salvo.getDataCadastro());
        assertNull(salvo.getDataBaixa());
    }

    @Test
    void cadastrarComValidadeNoPassado_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.minusDays(1)));
    }

    @Test
    void cadastrarComValidadeHojeEmBrasilia_devePermitirMesmoJaSendoAmanhaEmUTC() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE);

        assertEquals(HOJE, ambiente.lote(USUARIO, produtoDeTeste, lote.getId()).getDataValidade());
    }

    @Test
    void cadastrarComQuantidadeZero_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 0, 10.0, HOJE.plusDays(5)));
    }

    @Test
    void cadastrarComPrecoNegativo_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, -5.0, HOJE.plusDays(5)));
    }

    @Test
    void cadastrarSemValidade_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 5.0, null));
    }

    @Test
    void listarAtivosFEFO_deveOrdenarPorValidadeMaisProximaPrimeiro() {
        Produto outroProduto = ambiente.novoProduto(USUARIO);
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(30));
        loteService.cadastrar(USUARIO, outroProduto.getId(), 1, 10.0, HOJE.plusDays(3));
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(10));

        List<LoteDoProduto> ativos = loteService.listarAtivosFEFO(USUARIO);

        assertEquals(3, ativos.size());
        assertEquals(HOJE.plusDays(3), ativos.get(0).lote().getDataValidade());
        assertEquals(outroProduto.getNome(), ativos.get(0).nomeProduto());
        assertEquals(HOJE.plusDays(10), ativos.get(1).lote().getDataValidade());
        assertEquals(HOJE.plusDays(30), ativos.get(2).lote().getDataValidade());
    }

    @Test
    void listarAtivosFEFO_naoDeveTrazerLotesConsumidosNemDescartados() {
        Lote consumido = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(2));
        Lote descartado = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(3));
        Lote ativo = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(4));
        loteService.consumir(USUARIO, produtoDeTeste.getId(), consumido.getId());
        loteService.descartar(USUARIO, produtoDeTeste.getId(), descartado.getId());

        List<LoteDoProduto> ativos = loteService.listarAtivosFEFO(USUARIO);

        assertEquals(1, ativos.size());
        assertEquals(ativo.getId(), ativos.get(0).lote().getId());
    }

    @Test
    void listarAlertaVencimento_deveIncluirItensDentroDaJanelaEOsVencidos() {
        ambiente.loteVencido(USUARIO, produtoDeTeste, 1, 10.0, 2);
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(3));
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(7));
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(8));
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(30));

        List<LocalDate> validades = loteService.listarAlertaVencimento(USUARIO).stream()
                .map(l -> l.lote().getDataValidade())
                .toList();

        assertEquals(List.of(HOJE.minusDays(2), HOJE.plusDays(3), HOJE.plusDays(7)), validades);
    }

    @Test
    void descartar_deveSomarValorNaPerdaFinanceiraTotal() {
        double perdaAntes = loteService.calcularPerdaFinanceiraTotal(USUARIO);

        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 3, 12.50, HOJE.plusDays(5));
        loteService.descartar(USUARIO, produtoDeTeste.getId(), lote.getId());

        double perdaDepois = loteService.calcularPerdaFinanceiraTotal(USUARIO);

        assertEquals(perdaAntes + 37.50, perdaDepois, 0.001);
        Lote descartado = ambiente.lote(USUARIO, produtoDeTeste, lote.getId());
        assertEquals(StatusLote.DESCARTADO, descartado.getStatus());
        assertEquals(AGORA, descartado.getDataBaixa());
    }

    @Test
    void consumir_naoDeveAfetarPerdaFinanceira() {
        double perdaAntes = loteService.calcularPerdaFinanceiraTotal(USUARIO);

        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 5, 100.0, HOJE.plusDays(5));
        loteService.consumir(USUARIO, produtoDeTeste.getId(), lote.getId());

        assertEquals(perdaAntes, loteService.calcularPerdaFinanceiraTotal(USUARIO), 0.001);
        assertEquals(StatusLote.CONSUMIDO, ambiente.lote(USUARIO, produtoDeTeste, lote.getId()).getStatus());
    }

    @Test
    void consumirOuDescartarLoteJaBaixado_deveLancarValidacaoException() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(5));
        loteService.consumir(USUARIO, produtoDeTeste.getId(), lote.getId());

        assertThrows(ValidacaoException.class,
                () -> loteService.descartar(USUARIO, produtoDeTeste.getId(), lote.getId()));
        assertThrows(ValidacaoException.class,
                () -> loteService.consumir(USUARIO, produtoDeTeste.getId(), lote.getId()));
        assertEquals(StatusLote.CONSUMIDO, ambiente.lote(USUARIO, produtoDeTeste, lote.getId()).getStatus());
        assertEquals(0.0, loteService.calcularPerdaFinanceiraTotal(USUARIO), 0.001);
    }

    @Test
    void descartarParte_deveManterORestanteAtivoECriarRegistroDescartado() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 4, 2.5, HOJE.plusDays(3));

        Lote descartado = loteService.descartar(USUARIO, produtoDeTeste.getId(), lote.getId(), 1);

        Lote original = ambiente.lote(USUARIO, produtoDeTeste, lote.getId());
        assertEquals(StatusLote.ATIVO, original.getStatus());
        assertEquals(3, original.getQuantidade());
        Lote registro = ambiente.lote(USUARIO, produtoDeTeste, descartado.getId());
        assertEquals(StatusLote.DESCARTADO, registro.getStatus());
        assertEquals(1, registro.getQuantidade());
        assertEquals(lote.getDataValidade(), registro.getDataValidade());
        assertEquals(2.5, registro.getPrecoUnitario());
        assertEquals(AGORA, registro.getDataBaixa());
        assertEquals(2.5, loteService.calcularPerdaFinanceiraTotal(USUARIO), 0.001);
    }

    @Test
    void descartarOLoteInteiroPelaQuantidade_deveSoMudarOStatus() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 4, 2.5, HOJE.plusDays(3));

        loteService.descartar(USUARIO, produtoDeTeste.getId(), lote.getId(), 4);

        List<Lote> lotes = ambiente.lotes(USUARIO, produtoDeTeste);
        assertEquals(1, lotes.size());
        assertEquals(StatusLote.DESCARTADO, lotes.get(0).getStatus());
        assertEquals(10.0, loteService.calcularPerdaFinanceiraTotal(USUARIO), 0.001);
    }

    @Test
    void descartarMaisQueOLoteOuZero_deveSerRecusadoSemAlterarNada() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 4, 2.5, HOJE.plusDays(3));
        List<String> antes = resumoDosLotes();

        ValidacaoException erro = assertThrows(ValidacaoException.class,
                () -> loteService.descartar(USUARIO, produtoDeTeste.getId(), lote.getId(), 5));
        assertThrows(ValidacaoException.class,
                () -> loteService.descartar(USUARIO, produtoDeTeste.getId(), lote.getId(), 0));

        assertTrue(erro.getMessage().contains("(4)"));
        assertEquals(antes, resumoDosLotes());
    }

    @Test
    void listarHistorico_deveTrazerConsumidosEDescartadosComFiltroOpcional() {
        Lote consumido = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 4.0, HOJE.plusDays(1));
        Lote descartado = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 4.0, HOJE.plusDays(2));
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 4.0, HOJE.plusDays(3));
        loteService.consumir(USUARIO, produtoDeTeste.getId(), consumido.getId());
        loteService.descartar(USUARIO, produtoDeTeste.getId(), descartado.getId());

        assertEquals(2, loteService.listarHistorico(USUARIO, null).size());
        assertEquals(List.of(consumido.getId()), loteService.listarHistorico(USUARIO, StatusLote.CONSUMIDO).stream()
                .map(l -> l.lote().getId()).toList());
        assertEquals(List.of(descartado.getId()), loteService.listarHistorico(USUARIO, StatusLote.DESCARTADO).stream()
                .map(l -> l.lote().getId()).toList());
        assertTrue(loteService.listarHistorico(USUARIO, StatusLote.ATIVO).isEmpty());
    }

    @Test
    void desfazerConsumoParcial_deveDevolverAQuantidadeAoLoteDeOrigem() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 4, 2.0, HOJE.plusDays(10));
        loteService.consumirPorFEFO(USUARIO, produtoDeTeste.getId(), 1);
        Lote consumido = ambiente.lotes(USUARIO, produtoDeTeste).stream()
                .filter(l -> l.getStatus() == StatusLote.CONSUMIDO).findFirst().orElseThrow();

        Lote devolvido = loteService.desfazerBaixa(USUARIO, produtoDeTeste.getId(), consumido.getId());

        List<Lote> lotes = ambiente.lotes(USUARIO, produtoDeTeste);
        assertEquals(1, lotes.size());
        assertEquals(lote.getId(), devolvido.getId());
        assertEquals(StatusLote.ATIVO, lotes.get(0).getStatus());
        assertEquals(4, lotes.get(0).getQuantidade());
        assertTrue(loteService.listarHistorico(USUARIO, null).isEmpty());
    }

    @Test
    void desfazerDescarteDoLoteInteiro_deveVoltarComoAtivoETirarDasPerdas() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 3, 5.0, HOJE.plusDays(2));
        double perdaAntes = loteService.calcularPerdaFinanceiraTotal(USUARIO);
        loteService.descartar(USUARIO, produtoDeTeste.getId(), lote.getId());
        assertEquals(perdaAntes + 15.0, loteService.calcularPerdaFinanceiraTotal(USUARIO), 0.001);

        loteService.desfazerBaixa(USUARIO, produtoDeTeste.getId(), lote.getId());

        Lote devolvido = ambiente.lote(USUARIO, produtoDeTeste, lote.getId());
        assertEquals(StatusLote.ATIVO, devolvido.getStatus());
        assertEquals(3, devolvido.getQuantidade());
        assertEquals(HOJE.plusDays(2), devolvido.getDataValidade());
        assertEquals(5.0, devolvido.getPrecoUnitario());
        assertNull(devolvido.getDataBaixa());
        assertEquals(perdaAntes, loteService.calcularPerdaFinanceiraTotal(USUARIO), 0.001);
    }

    @Test
    void desfazerDescarteParcial_deveJuntarAoLoteEVoltarOTotalDePerdas() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 4, 2.5, HOJE.plusDays(3));
        Lote descartado = loteService.descartar(USUARIO, produtoDeTeste.getId(), lote.getId(), 1);

        loteService.desfazerBaixa(USUARIO, produtoDeTeste.getId(), descartado.getId());

        List<Lote> lotes = ambiente.lotes(USUARIO, produtoDeTeste);
        assertEquals(1, lotes.size());
        assertEquals(4, lotes.get(0).getQuantidade());
        assertEquals(0.0, loteService.calcularPerdaFinanceiraTotal(USUARIO), 0.001);
    }

    @Test
    void desfazerQuandoOLoteDeOrigemJaFoiBaixado_deveVoltarComoUmLoteSeparado() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 4, 2.0, HOJE.plusDays(10));
        Lote descartado = loteService.descartar(USUARIO, produtoDeTeste.getId(), lote.getId(), 1);
        loteService.consumir(USUARIO, produtoDeTeste.getId(), lote.getId());

        loteService.desfazerBaixa(USUARIO, produtoDeTeste.getId(), descartado.getId());

        assertEquals(StatusLote.ATIVO, ambiente.lote(USUARIO, produtoDeTeste, descartado.getId()).getStatus());
        assertEquals(StatusLote.CONSUMIDO, ambiente.lote(USUARIO, produtoDeTeste, lote.getId()).getStatus());
    }

    @Test
    void desfazerItemQueJaEstaNaDespensaOuQueNaoExiste_deveSerRecusado() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 2.0, HOJE.plusDays(10));

        assertThrows(ValidacaoException.class,
                () -> loteService.desfazerBaixa(USUARIO, produtoDeTeste.getId(), lote.getId()));
        assertThrows(ValidacaoException.class,
                () -> loteService.desfazerBaixa(USUARIO, produtoDeTeste.getId(), "nao-existe"));
    }

    @Test
    void resumirDesperdicio_deveCalcularDescartadoSobreConsumidoMaisDescartado() {
        Lote consumido = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 3, 10.0, HOJE.plusDays(5));
        Lote descartado = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(1));
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 9, 10.0, HOJE.plusDays(9));
        loteService.consumir(USUARIO, produtoDeTeste.getId(), consumido.getId());
        loteService.descartar(USUARIO, produtoDeTeste.getId(), descartado.getId());

        ResumoDesperdicio resumo = loteService.resumirDesperdicio(USUARIO);

        assertTrue(resumo.temDados());
        assertEquals(30.0, resumo.totalConsumido(), 0.001);
        assertEquals(10.0, resumo.totalDescartado(), 0.001);
        assertEquals(25.0, resumo.percentualDesperdicio().getAsDouble(), 0.001);
    }

    @Test
    void resumirDesperdicio_semNadaConsumidoNemDescartado_naoDeveTerPercentual() {
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 3, 10.0, HOJE.plusDays(5));

        ResumoDesperdicio resumo = loteService.resumirDesperdicio(USUARIO);

        assertFalse(resumo.temDados());
        assertTrue(resumo.percentualDesperdicio().isEmpty());
    }

    @Test
    void resumirDesperdicio_soComDescartesOuSoComConsumos_deveDar100Ou0PorCento() {
        assertEquals(100.0, new ResumoDesperdicio(0, 8).percentualDesperdicio().getAsDouble(), 0.001);
        assertEquals(0.0, new ResumoDesperdicio(8, 0).percentualDesperdicio().getAsDouble(), 0.001);
    }

    @Test
    void listarDescartados_deveTrazerSoOsLotesDescartados() {
        Lote descartado = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 2, 4.0, HOJE.plusDays(1));
        Lote consumido = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 4.0, HOJE.plusDays(2));
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 4.0, HOJE.plusDays(3));
        loteService.descartar(USUARIO, produtoDeTeste.getId(), descartado.getId());
        loteService.consumir(USUARIO, produtoDeTeste.getId(), consumido.getId());

        List<LoteDoProduto> descartados = loteService.listarDescartados(USUARIO);

        assertEquals(1, descartados.size());
        assertEquals(descartado.getId(), descartados.get(0).lote().getId());
        assertEquals(produtoDeTeste.getNome(), descartados.get(0).nomeProduto());
    }

    @Test
    void editar_deveAtualizarQuantidadePrecoEValidade() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(5));

        LocalDate novaValidade = HOJE.plusDays(20);
        loteService.editar(USUARIO, produtoDeTeste.getId(), lote.getId(), 4, 25.0, novaValidade);

        Lote atualizado = ambiente.lote(USUARIO, produtoDeTeste, lote.getId());
        assertEquals(4, atualizado.getQuantidade());
        assertEquals(25.0, atualizado.getPrecoUnitario());
        assertEquals(novaValidade, atualizado.getDataValidade());
    }

    @Test
    void editarAValidade_deveZerarOsAvisosEnviadosDoLote() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(2));
        marcarAvisos(lote);

        loteService.editar(USUARIO, produtoDeTeste.getId(), lote.getId(), 1, 10.0, HOJE.plusDays(9));

        Lote editado = ambiente.lote(USUARIO, produtoDeTeste, lote.getId());
        assertNull(editado.getAvisoVencendoEm());
        assertNull(editado.getAvisoVencidoEm());
    }

    @Test
    void editarSemMudarAValidade_deveManterOsAvisos() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 10.0, HOJE.plusDays(2));
        marcarAvisos(lote);

        loteService.editar(USUARIO, produtoDeTeste.getId(), lote.getId(), 3, 12.0, HOJE.plusDays(2));

        assertNotNull(ambiente.lote(USUARIO, produtoDeTeste, lote.getId()).getAvisoVencendoEm());
    }

    private void marcarAvisos(Lote lote) {
        Produto produto = ambiente.recarregar(USUARIO, produtoDeTeste);
        produto.buscarLote(lote.getId()).orElseThrow().setAvisoVencendoEm(AGORA);
        produto.buscarLote(lote.getId()).orElseThrow().setAvisoVencidoEm(AGORA);
        repositorio.save(produto);
    }

    @Test
    void cadastrarPorValidade_deveCriarUmLotePorDataComAQuantidadeDeCadaUma() {
        Map<LocalDate, Integer> quantidadePorValidade = new LinkedHashMap<>();
        quantidadePorValidade.put(HOJE.plusDays(20), 3);
        quantidadePorValidade.put(HOJE.plusDays(5), 2);

        List<Lote> cadastrados = loteService.cadastrarPorValidade(USUARIO, produtoDeTeste.getId(),
                quantidadePorValidade, 8.0);

        List<LoteDoProduto> ativos = loteService.listarAtivosFEFO(USUARIO);

        assertEquals(2, cadastrados.size());
        assertEquals(2, ativos.size());
        assertEquals(HOJE.plusDays(5), ativos.get(0).lote().getDataValidade());
        assertEquals(2, ativos.get(0).lote().getQuantidade());
        assertEquals(HOJE.plusDays(20), ativos.get(1).lote().getDataValidade());
        assertEquals(3, ativos.get(1).lote().getQuantidade());
    }

    @Test
    void cadastrarPorValidade_deveAplicarOMesmoPrecoATodosOsLotes() {
        Map<LocalDate, Integer> quantidadePorValidade = new LinkedHashMap<>();
        quantidadePorValidade.put(HOJE.plusDays(3), 1);
        quantidadePorValidade.put(HOJE.plusDays(9), 1);
        quantidadePorValidade.put(HOJE.plusDays(15), 4);

        loteService.cadastrarPorValidade(USUARIO, produtoDeTeste.getId(), quantidadePorValidade, 6.75);

        List<Lote> lotes = ambiente.lotes(USUARIO, produtoDeTeste);
        assertEquals(3, lotes.size());
        assertTrue(lotes.stream().allMatch(l -> l.getPrecoUnitario() == 6.75));
    }

    @Test
    void cadastrarPorValidade_comUmaDataVencida_naoDeveSalvarNenhumLote() {
        Map<LocalDate, Integer> quantidadePorValidade = new LinkedHashMap<>();
        quantidadePorValidade.put(HOJE.plusDays(10), 2);
        quantidadePorValidade.put(HOJE.minusDays(1), 1);

        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrarPorValidade(USUARIO, produtoDeTeste.getId(), quantidadePorValidade, 5.0));

        assertTrue(ambiente.lotes(USUARIO, produtoDeTeste).isEmpty());
    }

    @Test
    void cadastrarPorValidade_semNenhumaValidade_deveLancarValidacaoException() {
        assertThrows(ValidacaoException.class, () ->
                loteService.cadastrarPorValidade(USUARIO, produtoDeTeste.getId(), new LinkedHashMap<>(), 5.0));
    }

    @Test
    void cadastrarPorValidade_deveGravarTodosOsLotesNumaUnicaOperacao() {
        int gravacoesAntes = repositorio.getGravacoes();
        Map<LocalDate, Integer> quantidadePorValidade = new LinkedHashMap<>();
        quantidadePorValidade.put(HOJE.plusDays(3), 1);
        quantidadePorValidade.put(HOJE.plusDays(9), 2);

        loteService.cadastrarPorValidade(USUARIO, produtoDeTeste.getId(), quantidadePorValidade, 3.0);

        assertEquals(gravacoesAntes + 1, repositorio.getGravacoes());
    }

    @Test
    void agruparPorValidade_deveSomarAsUnidadesComAMesmaValidade() {
        LocalDate validadeA = HOJE.plusDays(4);
        LocalDate validadeB = HOJE.plusDays(12);

        Map<LocalDate, Integer> agrupado = LoteService.agruparPorValidade(
                List.of(validadeA, validadeB, validadeA, validadeA));

        assertEquals(2, agrupado.size());
        assertEquals(3, agrupado.get(validadeA));
        assertEquals(1, agrupado.get(validadeB));
    }

    @Test
    void agruparPorValidade_comTodasAsValidadesIguais_deveGerarUmUnicoGrupo() {
        LocalDate validade = HOJE.plusDays(7);

        assertEquals(Map.of(validade, 3), LoteService.agruparPorValidade(List.of(validade, validade, validade)));
    }

    @Test
    void agruparPorValidade_comTodasAsValidadesDiferentes_deveGerarUmGrupoPorItem() {
        LocalDate validadeA = HOJE.plusDays(2);
        LocalDate validadeB = HOJE.plusDays(8);
        LocalDate validadeC = HOJE.plusDays(30);

        Map<LocalDate, Integer> agrupado = LoteService.agruparPorValidade(List.of(validadeC, validadeA, validadeB));

        assertEquals(List.of(validadeC, validadeA, validadeB), List.copyOf(agrupado.keySet()));
        assertTrue(agrupado.values().stream().allMatch(quantidade -> quantidade == 1));
    }

    @Test
    void agruparPorValidade_semItens_deveRetornarMapaVazio() {
        assertTrue(LoteService.agruparPorValidade(List.of()).isEmpty());
    }

    @Test
    void agruparECadastrarPorValidade_deveSalvarUmLotePorDataComAQuantidadeSomada() {
        LocalDate validadeProxima = HOJE.plusDays(3);
        LocalDate validadeDistante = HOJE.plusDays(25);

        Map<LocalDate, Integer> agrupado = LoteService.agruparPorValidade(
                List.of(validadeDistante, validadeProxima, validadeDistante, validadeProxima, validadeDistante));
        loteService.cadastrarPorValidade(USUARIO, produtoDeTeste.getId(), agrupado, 4.5);

        List<LoteDoProduto> ativos = loteService.listarAtivosFEFO(USUARIO);

        assertEquals(2, ativos.size());
        assertEquals(validadeProxima, ativos.get(0).lote().getDataValidade());
        assertEquals(2, ativos.get(0).lote().getQuantidade());
        assertEquals(validadeDistante, ativos.get(1).lote().getDataValidade());
        assertEquals(3, ativos.get(1).lote().getQuantidade());
        assertTrue(ativos.stream().allMatch(l -> l.lote().getPrecoUnitario() == 4.5));
    }

    @Test
    void consumirPorFEFO_dentroDeUmLote_deveManterORestanteAtivoECriarRegistroConsumido() {
        LocalDate validade = HOJE.plusDays(10);
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 5, 4.0, validade);

        loteService.consumirPorFEFO(USUARIO, produtoDeTeste.getId(), 2);

        List<Lote> todos = ambiente.lotes(USUARIO, produtoDeTeste);
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
        assertEquals(original.getDataCadastro(), consumido.getDataCadastro());
        assertEquals(AGORA, consumido.getDataBaixa());
    }

    @Test
    void consumirPorFEFO_passandoPorDoisLotes_deveConsumirPrimeiroOQueVenceAntes() {
        Lote distante = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 4, 6.0, HOJE.plusDays(20));
        Lote proximo = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 3, 5.0, HOJE.plusDays(2));

        List<ItemConsumo> plano = loteService.consumirPorFEFO(USUARIO, produtoDeTeste.getId(), 5);

        assertEquals(2, plano.size());
        assertEquals(proximo.getId(), plano.get(0).loteId());
        assertEquals(3, plano.get(0).quantidadeConsumida());
        assertTrue(plano.get(0).consomeLoteInteiro());
        assertEquals(distante.getId(), plano.get(1).loteId());
        assertEquals(2, plano.get(1).quantidadeConsumida());
        assertEquals(2, plano.get(1).quantidadeRestante());

        List<Lote> todos = ambiente.lotes(USUARIO, produtoDeTeste);
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
        assertEquals(HOJE.plusDays(20), novosConsumidos.get(0).getDataValidade());
        assertEquals(6.0, novosConsumidos.get(0).getPrecoUnitario());
    }

    @Test
    void consumirPorFEFO_doLoteInteiro_deveSoMudarStatusParaConsumido() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 3, 7.0, HOJE.plusDays(5));

        loteService.consumirPorFEFO(USUARIO, produtoDeTeste.getId(), 3);

        List<Lote> todos = ambiente.lotes(USUARIO, produtoDeTeste);
        assertEquals(1, todos.size());
        assertEquals(lote.getId(), todos.get(0).getId());
        assertEquals(StatusLote.CONSUMIDO, todos.get(0).getStatus());
        assertEquals(3, todos.get(0).getQuantidade());
        assertEquals(AGORA, todos.get(0).getDataBaixa());
    }

    @Test
    void consumirPorFEFO_deveIgnorarLotesVencidos() {
        Lote vencido = ambiente.loteVencido(USUARIO, produtoDeTeste, 2, 3.0, 2);
        Lote valido = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 4, 3.0, HOJE.plusDays(8));

        List<ItemConsumo> plano = loteService.consumirPorFEFO(USUARIO, produtoDeTeste.getId(), 1);

        assertEquals(1, plano.size());
        assertEquals(valido.getId(), plano.get(0).loteId());

        List<Lote> todos = ambiente.lotes(USUARIO, produtoDeTeste);
        Lote vencidoDepois = loteComId(todos, vencido.getId());
        assertEquals(StatusLote.ATIVO, vencidoDepois.getStatus());
        assertEquals(2, vencidoDepois.getQuantidade());
        assertEquals(3, loteComId(todos, valido.getId()).getQuantidade());
    }

    @Test
    void consumirPorFEFO_loteQueVenceHojeEmBrasilia_aindaPodeSerConsumido() {
        Lote lote = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 3.0, HOJE);

        loteService.consumirPorFEFO(USUARIO, produtoDeTeste.getId(), 1);

        assertEquals(StatusLote.CONSUMIDO, ambiente.lote(USUARIO, produtoDeTeste, lote.getId()).getStatus());
    }

    @Test
    void consumirPorFEFO_comQuantidadeMaiorQueODisponivel_naoDeveAlterarNada() {
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 2, 5.0, HOJE.plusDays(3));
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 1, 5.0, HOJE.plusDays(9));
        ambiente.loteVencido(USUARIO, produtoDeTeste, 10, 5.0, 1);

        List<String> antes = resumoDosLotes();
        int gravacoesAntes = repositorio.getGravacoes();

        ValidacaoException erro = assertThrows(ValidacaoException.class, () ->
                loteService.consumirPorFEFO(USUARIO, produtoDeTeste.getId(), 4));

        assertTrue(erro.getMessage().contains("3"));
        assertEquals(antes, resumoDosLotes());
        assertEquals(gravacoesAntes, repositorio.getGravacoes());
    }

    @Test
    void consumirPorFEFO_comProdutoSoComLotesVencidos_deveSugerirODescarte() {
        ambiente.loteVencido(USUARIO, produtoDeTeste, 2, 5.0, 3);

        List<String> antes = resumoDosLotes();

        ValidacaoException erro = assertThrows(ValidacaoException.class, () ->
                loteService.consumirPorFEFO(USUARIO, produtoDeTeste.getId(), 1));

        assertTrue(erro.getMessage().contains("vencidos"));
        assertTrue(erro.getMessage().contains("descartados"));
        assertEquals(antes, resumoDosLotes());
    }

    @Test
    void consumirPorFEFO_comQuantidadeZero_deveLancarValidacaoException() {
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 2, 5.0, HOJE.plusDays(3));

        assertThrows(ValidacaoException.class, () -> loteService.consumirPorFEFO(USUARIO, produtoDeTeste.getId(), 0));
    }

    @Test
    void planejarConsumoFEFO_deveMostrarOsLotesSemGravarNada() {
        Lote distante = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 4, 6.0, HOJE.plusDays(20));
        Lote proximo = loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 3, 5.0, HOJE.plusDays(2));
        List<String> antes = resumoDosLotes();
        int gravacoesAntes = repositorio.getGravacoes();

        List<ItemConsumo> plano = loteService.planejarConsumoFEFO(USUARIO, produtoDeTeste.getId(), 4);

        assertEquals(List.of(proximo.getId(), distante.getId()), plano.stream().map(ItemConsumo::loteId).toList());
        assertEquals(3, plano.get(0).quantidadeConsumida());
        assertEquals(1, plano.get(1).quantidadeConsumida());
        assertEquals(3, plano.get(1).quantidadeRestante());
        assertEquals(antes, resumoDosLotes());
        assertEquals(gravacoesAntes, repositorio.getGravacoes());
    }

    @Test
    void consumirPorFEFO_comAlteracaoConcorrenteNoProduto_naoDeveGravarNada() {
        loteService.cadastrar(USUARIO, produtoDeTeste.getId(), 5, 4.0, HOJE.plusDays(10));
        List<String> antes = resumoDosLotes();

        repositorio.simularAlteracaoConcorrenteNaProximaGravacao();

        assertThrows(OptimisticLockingFailureException.class,
                () -> loteService.consumirPorFEFO(USUARIO, produtoDeTeste.getId(), 2));
        assertEquals(antes, resumoDosLotes());
    }

    private List<String> resumoDosLotes() {
        return ambiente.lotes(USUARIO, produtoDeTeste).stream()
                .map(l -> l.getId() + "|" + l.getStatus() + "|" + l.getQuantidade() + "|" + l.getDataBaixa())
                .toList();
    }

    private static Lote loteComId(List<Lote> lotes, String id) {
        return lotes.stream().filter(l -> l.getId().equals(id)).findFirst().orElseThrow();
    }
}
