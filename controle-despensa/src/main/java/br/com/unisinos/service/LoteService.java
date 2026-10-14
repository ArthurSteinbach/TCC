package br.com.unisinos.service;

import br.com.unisinos.dao.LoteDAO;
import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.ItemConsumo;
import br.com.unisinos.model.Lote;
import br.com.unisinos.util.Constantes;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regras de negócio relacionadas aos lotes: cadastro com validação,
 * consulta FEFO, alerta de vencimento e baixa (consumo/descarte).
 * Concentra a lógica descrita na Seção 4.3 do TCC I (estruturação da
 * regra de negócio FEFO).
 */
public class LoteService {

    private final LoteDAO loteDAO;

    public LoteService() {
        this.loteDAO = new LoteDAO();
    }

    /** Construtor alternativo, útil para testes com um DAO substituto. */
    public LoteService(LoteDAO loteDAO) {
        this.loteDAO = loteDAO;
    }

    /**
     * Cadastra um novo lote. Aplica o fluxo de entrada e validação descrito
     * no TCC I (4.3.1): quantidade e preço válidos, e a validade não pode
     * estar no passado — impedindo o armazenamento de itens já impróprios
     * para consumo.
     */
    public Lote cadastrar(Integer produtoId, double quantidade, double precoUnitario, LocalDate dataValidade)
            throws SQLException {

        validarCadastro(produtoId, quantidade, precoUnitario, dataValidade);

        Lote lote = new Lote(produtoId, quantidade, precoUnitario, dataValidade);
        return loteDAO.inserir(lote);
    }

    public List<Lote> cadastrarPorValidade(Integer produtoId, Map<LocalDate, Integer> quantidadePorValidade,
                                           double precoUnitario) throws SQLException {

        if (quantidadePorValidade == null || quantidadePorValidade.isEmpty()) {
            throw new ValidacaoException("Informe a validade de pelo menos um item.");
        }
        for (Map.Entry<LocalDate, Integer> entrada : quantidadePorValidade.entrySet()) {
            validarCadastro(produtoId, entrada.getValue(), precoUnitario, entrada.getKey());
        }

        List<Lote> cadastrados = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> entrada : quantidadePorValidade.entrySet()) {
            Lote lote = new Lote(produtoId, entrada.getValue(), precoUnitario, entrada.getKey());
            cadastrados.add(loteDAO.inserir(lote));
        }
        return cadastrados;
    }

    public static Map<LocalDate, Integer> agruparPorValidade(List<LocalDate> validadesDosItens) {
        Map<LocalDate, Integer> quantidadePorValidade = new LinkedHashMap<>();
        for (LocalDate validade : validadesDosItens) {
            quantidadePorValidade.merge(validade, 1, Integer::sum);
        }
        return quantidadePorValidade;
    }

    private void validarCadastro(Integer produtoId, double quantidade, double precoUnitario, LocalDate dataValidade) {
        if (produtoId == null) {
            throw new ValidacaoException("É necessário informar o produto do lote.");
        }
        if (quantidade <= 0) {
            throw new ValidacaoException("A quantidade deve ser maior que zero.");
        }
        if (precoUnitario < 0) {
            throw new ValidacaoException("O preço não pode ser negativo.");
        }
        if (dataValidade == null) {
            throw new ValidacaoException("A data de validade é obrigatória.");
        }
        if (dataValidade.isBefore(LocalDate.now())) {
            throw new ValidacaoException(
                    "Não é possível cadastrar um lote já vencido (validade informada: " + dataValidade + ").");
        }
    }

    /**
     * Edita quantidade, preço e validade de um lote já existente.
     * Diferente do cadastro, aqui NÃO bloqueamos data de validade no
     * passado — o usuário pode estar apenas corrigindo o preço de um
     * item que já está vencido, sem querer mexer no status dele.
     */
    public void editar(int loteId, double quantidade, double precoUnitario, LocalDate dataValidade)
            throws SQLException {

        if (quantidade <= 0) {
            throw new ValidacaoException("A quantidade deve ser maior que zero.");
        }
        if (precoUnitario < 0) {
            throw new ValidacaoException("O preço não pode ser negativo.");
        }
        if (dataValidade == null) {
            throw new ValidacaoException("A data de validade é obrigatória.");
        }

        Lote lote = new Lote();
        lote.setId(loteId);
        lote.setQuantidade(quantidade);
        lote.setPrecoUnitario(precoUnitario);
        lote.setDataValidade(dataValidade);
        loteDAO.atualizar(lote);
    }

    /** Lista os lotes ativos ordenados pela regra FEFO (validade mais próxima primeiro). */
    public List<Lote> listarAtivosFEFO() throws SQLException {
        return loteDAO.listarAtivosFEFO();
    }

    /**
     * Lista os lotes que devem entrar no alerta preventivo de vencimento,
     * usando o prazo padrão do sistema (Constantes.DIAS_ALERTA_VENCIMENTO).
     */
    public List<Lote> listarAlertaVencimento() throws SQLException {
        return loteDAO.listarVencendoEm(Constantes.DIAS_ALERTA_VENCIMENTO);
    }

    /** Lista os lotes descartados por vencimento, para o relatório de perdas. */
    public List<Lote> listarDescartados() throws SQLException {
        return loteDAO.listarDescartados();
    }

    /** Dá baixa por consumo normal: o usuário utilizou o item. */
    public void consumir(int loteId) throws SQLException {
        loteDAO.marcarComoConsumido(loteId);
    }

    public List<ItemConsumo> planejarConsumoFEFO(Integer produtoId, double quantidade) throws SQLException {
        if (produtoId == null) {
            throw new ValidacaoException("É necessário informar o produto a consumir.");
        }
        if (quantidade <= 0) {
            throw new ValidacaoException("A quantidade a consumir deve ser maior que zero.");
        }

        List<Lote> ativos = loteDAO.listarAtivosPorProdutoFEFO(produtoId);
        if (ativos.isEmpty()) {
            throw new ValidacaoException("Este produto não tem lotes ativos na despensa.");
        }

        LocalDate hoje = LocalDate.now();
        List<Lote> naoVencidos = ativos.stream()
                .filter(l -> !l.getDataValidade().isBefore(hoje))
                .toList();
        if (naoVencidos.isEmpty()) {
            throw new ValidacaoException("Todos os lotes ativos deste produto estão vencidos. "
                    + "Eles não podem ser consumidos; marque-os como descartados.");
        }

        double disponivel = naoVencidos.stream().mapToDouble(Lote::getQuantidade).sum();
        if (quantidade > disponivel) {
            throw new ValidacaoException(String.format(
                    "Quantidade indisponível: há apenas %s unidade(s) dentro da validade.", formatar(disponivel)));
        }

        List<ItemConsumo> plano = new ArrayList<>();
        double restante = quantidade;
        for (Lote lote : naoVencidos) {
            if (restante <= 0) {
                break;
            }
            double consumida = Math.min(restante, lote.getQuantidade());
            plano.add(new ItemConsumo(lote, consumida));
            restante -= consumida;
        }
        return plano;
    }

    public List<ItemConsumo> consumirPorFEFO(Integer produtoId, double quantidade) throws SQLException {
        List<ItemConsumo> plano = planejarConsumoFEFO(produtoId, quantidade);
        loteDAO.registrarConsumo(plano);
        return plano;
    }

    private static String formatar(double quantidade) {
        return quantidade == Math.rint(quantidade)
                ? String.valueOf((long) quantidade)
                : String.valueOf(quantidade);
    }

    /** Dá baixa por descarte: o item venceu e foi jogado fora (gera perda financeira). */
    public void descartar(int loteId) throws SQLException {
        loteDAO.marcarComoDescartado(loteId);
    }

    /** Retorna o total acumulado de perdas financeiras (soma dos lotes descartados). */
    public double calcularPerdaFinanceiraTotal() throws SQLException {
        return loteDAO.calcularPerdaFinanceiraTotal();
    }
}
