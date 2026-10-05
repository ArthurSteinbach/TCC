package br.com.unisinos.service;

import br.com.unisinos.dao.LoteDAO;
import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.Lote;
import br.com.unisinos.util.Constantes;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
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

    /** Dá baixa por descarte: o item venceu e foi jogado fora (gera perda financeira). */
    public void descartar(int loteId) throws SQLException {
        loteDAO.marcarComoDescartado(loteId);
    }

    /** Retorna o total acumulado de perdas financeiras (soma dos lotes descartados). */
    public double calcularPerdaFinanceiraTotal() throws SQLException {
        return loteDAO.calcularPerdaFinanceiraTotal();
    }
}
