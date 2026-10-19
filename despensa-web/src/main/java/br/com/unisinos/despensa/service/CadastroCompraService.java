package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.integracao.ConsultaProdutoExterna;
import br.com.unisinos.despensa.integracao.InfoProdutoExterno;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.RegistroCompra;
import br.com.unisinos.despensa.model.ResultadoConsultaCodigo;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class CadastroCompraService {

    private static final Pattern CODIGO_DE_PRODUTO = Pattern.compile("\\d{8,14}");

    private final ProdutoService produtoService;
    private final LoteService loteService;
    private final ConsultaProdutoExterna consultaExterna;

    public CadastroCompraService(ProdutoService produtoService, LoteService loteService,
                                 ConsultaProdutoExterna consultaExterna) {
        this.produtoService = produtoService;
        this.loteService = loteService;
        this.consultaExterna = consultaExterna;
    }

    public ResultadoConsultaCodigo consultarCodigo(String usuarioId, String codigoBarras) {
        if (codigoBarras == null || codigoBarras.isBlank()) {
            return ResultadoConsultaCodigo.codigoVazio();
        }
        String codigo = codigoBarras.trim();

        Optional<Produto> existente = produtoService.buscarPorCodigoBarras(usuarioId, codigo);
        if (existente.isPresent()) {
            return ResultadoConsultaCodigo.existente(existente.get());
        }
        if (!CODIGO_DE_PRODUTO.matcher(codigo).matches()) {
            return ResultadoConsultaCodigo.semConsulta();
        }

        try {
            Optional<InfoProdutoExterno> info = consultaExterna.buscarPorCodigoBarras(codigo);
            return info.map(i -> ResultadoConsultaCodigo.encontrado(i.nome(), i.categoria()))
                    .orElseGet(ResultadoConsultaCodigo::naoEncontrado);
        } catch (IOException e) {
            return ResultadoConsultaCodigo.falha();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResultadoConsultaCodigo.falha();
        }
    }

    public RegistroCompra registrarCompra(String usuarioId, String codigoBarras, String nome, String categoria,
                                          Map<LocalDate, Integer> quantidadePorValidade, double precoUnitario) {
        Optional<Produto> existente = produtoService.buscarPorCodigoBarras(usuarioId, codigoBarras);
        if (existente.isPresent()) {
            Produto produto = existente.get();
            List<Lote> lotes = loteService.cadastrarPorValidade(usuarioId, produto.getId(), quantidadePorValidade,
                    precoUnitario);
            return new RegistroCompra(produto, lotes.size(), false);
        }

        Produto novo = loteService.cadastrarProdutoComLotes(usuarioId, codigoBarras, nome, categoria,
                quantidadePorValidade, precoUnitario);
        return new RegistroCompra(novo, novo.getLotes().size(), true);
    }
}
