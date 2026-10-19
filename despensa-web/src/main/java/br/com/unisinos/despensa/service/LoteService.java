package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.ItemConsumo;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.LoteDoProduto;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.StatusLote;
import br.com.unisinos.despensa.repository.ProdutoRepository;
import br.com.unisinos.despensa.util.Constantes;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Service
public class LoteService {

    private static final Comparator<LoteDoProduto> ORDEM_FEFO = Comparator
            .comparing((LoteDoProduto l) -> l.lote().getDataValidade())
            .thenComparing(l -> l.lote().getDataCadastro(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LoteDoProduto::nomeProduto);

    private final ProdutoService produtoService;
    private final ProdutoRepository produtoRepository;
    private final Clock relogio;

    public LoteService(ProdutoService produtoService, ProdutoRepository produtoRepository, Clock relogio) {
        this.produtoService = produtoService;
        this.produtoRepository = produtoRepository;
        this.relogio = relogio;
    }

    public Lote cadastrar(String usuarioId, String produtoId, double quantidade, double precoUnitario,
                          LocalDate dataValidade) {
        validarCadastro(quantidade, precoUnitario, dataValidade);

        Produto produto = produtoService.buscarPorId(usuarioId, produtoId);
        Lote lote = novoLote(quantidade, precoUnitario, dataValidade);
        produto.getLotes().add(lote);
        produtoRepository.save(produto);
        return lote;
    }

    public List<Lote> cadastrarPorValidade(String usuarioId, String produtoId,
                                           Map<LocalDate, Integer> quantidadePorValidade, double precoUnitario) {
        validarQuantidadesPorValidade(quantidadePorValidade, precoUnitario);

        Produto produto = produtoService.buscarPorId(usuarioId, produtoId);
        List<Lote> cadastrados = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> entrada : quantidadePorValidade.entrySet()) {
            cadastrados.add(novoLote(entrada.getValue(), precoUnitario, entrada.getKey()));
        }
        produto.getLotes().addAll(cadastrados);
        produtoRepository.save(produto);
        return cadastrados;
    }

    public Produto cadastrarProdutoComLotes(String usuarioId, String codigoBarras, String nome, String categoria,
                                            Map<LocalDate, Integer> quantidadePorValidade, double precoUnitario) {
        validarQuantidadesPorValidade(quantidadePorValidade, precoUnitario);

        Produto produto = produtoService.prepararNovo(usuarioId, codigoBarras, nome, categoria);
        for (Map.Entry<LocalDate, Integer> entrada : quantidadePorValidade.entrySet()) {
            produto.getLotes().add(novoLote(entrada.getValue(), precoUnitario, entrada.getKey()));
        }
        return produtoService.salvarNovo(produto);
    }

    public static Map<LocalDate, Integer> agruparPorValidade(List<LocalDate> validadesDosItens) {
        Map<LocalDate, Integer> quantidadePorValidade = new LinkedHashMap<>();
        for (LocalDate validade : validadesDosItens) {
            quantidadePorValidade.merge(validade, 1, Integer::sum);
        }
        return quantidadePorValidade;
    }

    public Lote editar(String usuarioId, String produtoId, String loteId, double quantidade, double precoUnitario,
                       LocalDate dataValidade) {
        if (quantidade <= 0) {
            throw new ValidacaoException("A quantidade deve ser maior que zero.");
        }
        if (precoUnitario < 0) {
            throw new ValidacaoException("O preço não pode ser negativo.");
        }
        if (dataValidade == null) {
            throw new ValidacaoException("A data de validade é obrigatória.");
        }

        Produto produto = produtoService.buscarPorId(usuarioId, produtoId);
        Lote lote = buscarLoteAtivo(produto, loteId);
        lote.setQuantidade(quantidade);
        lote.setPrecoUnitario(precoUnitario);
        lote.setDataValidade(dataValidade);
        produtoRepository.save(produto);
        return lote;
    }

    public void consumir(String usuarioId, String produtoId, String loteId) {
        darBaixa(usuarioId, produtoId, loteId, StatusLote.CONSUMIDO);
    }

    public void descartar(String usuarioId, String produtoId, String loteId) {
        darBaixa(usuarioId, produtoId, loteId, StatusLote.DESCARTADO);
    }

    public List<LoteDoProduto> listarAtivosFEFO(String usuarioId) {
        return listarLotes(usuarioId, Lote::isAtivo).stream()
                .sorted(ORDEM_FEFO)
                .toList();
    }

    public List<LoteDoProduto> listarAlertaVencimento(String usuarioId) {
        LocalDate limite = hoje().plusDays(Constantes.DIAS_ALERTA_VENCIMENTO);
        return listarAtivosFEFO(usuarioId).stream()
                .filter(l -> !l.lote().getDataValidade().isAfter(limite))
                .toList();
    }

    public List<LoteDoProduto> listarDescartados(String usuarioId) {
        return listarLotes(usuarioId, l -> l.getStatus() == StatusLote.DESCARTADO).stream()
                .sorted(Comparator.comparing((LoteDoProduto l) -> l.lote().getDataBaixa(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public double calcularPerdaFinanceiraTotal(String usuarioId) {
        return listarDescartados(usuarioId).stream()
                .mapToDouble(l -> l.lote().getValorTotal())
                .sum();
    }

    public List<ItemConsumo> planejarConsumoFEFO(String usuarioId, String produtoId, double quantidade) {
        validarQuantidadeConsumo(quantidade);
        return planejar(produtoService.buscarPorId(usuarioId, produtoId), quantidade);
    }

    public List<ItemConsumo> consumirPorFEFO(String usuarioId, String produtoId, double quantidade) {
        validarQuantidadeConsumo(quantidade);

        Produto produto = produtoService.buscarPorId(usuarioId, produtoId);
        List<ItemConsumo> plano = planejar(produto, quantidade);
        LocalDateTime agora = agora();

        for (ItemConsumo item : plano) {
            Lote lote = produto.buscarLote(item.loteId()).orElseThrow();
            if (item.consomeLoteInteiro()) {
                lote.setStatus(StatusLote.CONSUMIDO);
                lote.setDataBaixa(agora);
            } else {
                lote.setQuantidade(item.quantidadeRestante());
                Lote consumido = new Lote(novoId(), item.quantidadeConsumida(), lote.getPrecoUnitario(),
                        lote.getDataValidade(), lote.getDataCadastro());
                consumido.setStatus(StatusLote.CONSUMIDO);
                consumido.setDataBaixa(agora);
                produto.getLotes().add(consumido);
            }
        }

        produtoRepository.save(produto);
        return plano;
    }

    private List<ItemConsumo> planejar(Produto produto, double quantidade) {
        List<Lote> ativos = produto.getLotes().stream()
                .filter(Lote::isAtivo)
                .sorted(Comparator.comparing(Lote::getDataValidade)
                        .thenComparing(Lote::getDataCadastro, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (ativos.isEmpty()) {
            throw new ValidacaoException("Este produto não tem lotes ativos na despensa.");
        }

        LocalDate hoje = hoje();
        List<Lote> naoVencidos = ativos.stream()
                .filter(l -> !l.isVencidoEm(hoje))
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
            plano.add(new ItemConsumo(lote.getId(), lote.getDataValidade(), lote.getPrecoUnitario(),
                    lote.getQuantidade(), consumida));
            restante -= consumida;
        }
        return plano;
    }

    private void darBaixa(String usuarioId, String produtoId, String loteId, StatusLote novoStatus) {
        Produto produto = produtoService.buscarPorId(usuarioId, produtoId);
        Lote lote = buscarLoteAtivo(produto, loteId);
        lote.setStatus(novoStatus);
        lote.setDataBaixa(agora());
        produtoRepository.save(produto);
    }

    private List<LoteDoProduto> listarLotes(String usuarioId, Predicate<Lote> filtro) {
        List<LoteDoProduto> lotes = new ArrayList<>();
        for (Produto produto : produtoService.listar(usuarioId)) {
            for (Lote lote : produto.getLotes()) {
                if (filtro.test(lote)) {
                    lotes.add(new LoteDoProduto(produto.getId(), produto.getCodigoBarras(), produto.getNome(), lote));
                }
            }
        }
        return lotes;
    }

    private static Lote buscarLoteAtivo(Produto produto, String loteId) {
        Lote lote = produto.buscarLote(loteId)
                .orElseThrow(() -> new ValidacaoException("Lote não encontrado."));
        if (!lote.isAtivo()) {
            throw new ValidacaoException("Este lote já foi baixado (status " + lote.getStatus() + ").");
        }
        return lote;
    }

    private void validarQuantidadesPorValidade(Map<LocalDate, Integer> quantidadePorValidade, double precoUnitario) {
        if (quantidadePorValidade == null || quantidadePorValidade.isEmpty()) {
            throw new ValidacaoException("Informe a validade de pelo menos um item.");
        }
        for (Map.Entry<LocalDate, Integer> entrada : quantidadePorValidade.entrySet()) {
            validarCadastro(entrada.getValue() == null ? 0 : entrada.getValue(), precoUnitario, entrada.getKey());
        }
    }

    private void validarCadastro(double quantidade, double precoUnitario, LocalDate dataValidade) {
        if (quantidade <= 0) {
            throw new ValidacaoException("A quantidade deve ser maior que zero.");
        }
        if (precoUnitario < 0) {
            throw new ValidacaoException("O preço não pode ser negativo.");
        }
        if (dataValidade == null) {
            throw new ValidacaoException("A data de validade é obrigatória.");
        }
        if (dataValidade.isBefore(hoje())) {
            throw new ValidacaoException(
                    "Não é possível cadastrar um lote já vencido (validade informada: " + dataValidade + ").");
        }
    }

    private static void validarQuantidadeConsumo(double quantidade) {
        if (quantidade <= 0) {
            throw new ValidacaoException("A quantidade a consumir deve ser maior que zero.");
        }
    }

    private Lote novoLote(double quantidade, double precoUnitario, LocalDate dataValidade) {
        return new Lote(novoId(), quantidade, precoUnitario, dataValidade, agora());
    }

    private static String novoId() {
        return new ObjectId().toHexString();
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(relogio);
    }

    private static String formatar(double quantidade) {
        return quantidade == Math.rint(quantidade)
                ? String.valueOf((long) quantidade)
                : String.valueOf(quantidade);
    }
}
