package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.email.EnviadorDeEmail;
import br.com.unisinos.despensa.email.FalhaNoEnvioException;
import br.com.unisinos.despensa.email.ItemAviso;
import br.com.unisinos.despensa.email.MontadorDeEmails;
import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.ResultadoEnvioAlertas;
import br.com.unisinos.despensa.model.Usuario;
import br.com.unisinos.despensa.repository.ProdutoRepository;
import br.com.unisinos.despensa.repository.UsuarioRepository;
import br.com.unisinos.despensa.util.Constantes;
import br.com.unisinos.despensa.util.Tokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AlertaVencimentoService {

    private static final Logger LOG = LoggerFactory.getLogger(AlertaVencimentoService.class);

    private final UsuarioRepository usuarioRepository;
    private final ProdutoService produtoService;
    private final ProdutoRepository produtoRepository;
    private final EnviadorDeEmail enviador;
    private final MontadorDeEmails montador;
    private final Clock relogio;
    private final AtomicBoolean emExecucao = new AtomicBoolean();

    public AlertaVencimentoService(UsuarioRepository usuarioRepository, ProdutoService produtoService,
                                   ProdutoRepository produtoRepository, EnviadorDeEmail enviador,
                                   MontadorDeEmails montador, Clock relogio) {
        this.usuarioRepository = usuarioRepository;
        this.produtoService = produtoService;
        this.produtoRepository = produtoRepository;
        this.enviador = enviador;
        this.montador = montador;
        this.relogio = relogio;
    }

    public Optional<ResultadoEnvioAlertas> executar() {
        if (!emExecucao.compareAndSet(false, true)) {
            return Optional.empty();
        }
        try {
            return Optional.of(processar());
        } finally {
            emExecucao.set(false);
        }
    }

    public List<ItemAviso> novidades(String usuarioId) {
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate limite = hoje.plusDays(Constantes.DIAS_ALERTA_VENCIMENTO);
        List<ItemAviso> itens = new ArrayList<>();
        for (Produto produto : produtoService.listar(usuarioId)) {
            for (Lote lote : produto.getLotes()) {
                if (!lote.isAtivo()) {
                    continue;
                }
                boolean vencido = lote.isVencidoEm(hoje);
                boolean novoVencido = vencido && lote.getAvisoVencidoEm() == null;
                boolean novoVencendo = !vencido && !lote.getDataValidade().isAfter(limite)
                        && lote.getAvisoVencendoEm() == null;
                if (novoVencido || novoVencendo) {
                    itens.add(new ItemAviso(produto.getId(), lote.getId(), produto.getNome(), lote.getQuantidade(),
                            lote.getDataValidade(), ChronoUnit.DAYS.between(hoje, lote.getDataValidade())));
                }
            }
        }
        itens.sort(Comparator.comparing(ItemAviso::dataValidade).thenComparing(ItemAviso::nomeProduto));
        return itens;
    }

    private ResultadoEnvioAlertas processar() {
        LocalDate hoje = LocalDate.now(relogio);
        int verificados = 0;
        int enviados = 0;
        int falhas = 0;
        for (Usuario usuario : usuarioRepository.findAll()) {
            verificados++;
            try {
                if (avisar(usuario, hoje)) {
                    enviados++;
                }
            } catch (FalhaNoEnvioException e) {
                falhas++;
                LOG.warn("Falha ao enviar os avisos do usuário {}: {}", usuario.getId(), e.getMessage());
            } catch (RuntimeException e) {
                falhas++;
                LOG.warn("Erro ao preparar os avisos do usuário {}: {}", usuario.getId(), e.toString());
            }
        }
        LOG.info("Avisos de vencimento: {} usuário(s) verificado(s), {} e-mail(s) enviado(s), {} falha(s).",
                verificados, enviados, falhas);
        return new ResultadoEnvioAlertas(verificados, enviados, falhas);
    }

    private boolean avisar(Usuario usuario, LocalDate hoje) throws FalhaNoEnvioException {
        if (!usuario.recebeAvisos() || hoje.equals(usuario.getUltimoEnvioAlertas())) {
            return false;
        }
        List<ItemAviso> novidades = novidades(usuario.getId());
        if (novidades.isEmpty()) {
            return false;
        }

        Usuario destinatario = usuario;
        if (destinatario.getTokenDescadastro() == null) {
            destinatario.setTokenDescadastro(Tokens.novo());
            destinatario = usuarioRepository.save(destinatario);
        }

        List<ItemAviso> vencidos = novidades.stream().filter(ItemAviso::vencido).toList();
        List<ItemAviso> vencendo = novidades.stream().filter(item -> !item.vencido()).toList();
        enviador.enviar(montador.alertas(destinatario.getNome(), destinatario.emailDeDestinoDosAvisos(), vencidos,
                vencendo, destinatario.getTokenDescadastro()));

        marcarComoAvisados(destinatario.getId(), novidades);
        destinatario.setUltimoEnvioAlertas(hoje);
        usuarioRepository.save(destinatario);
        return true;
    }

    private void marcarComoAvisados(String usuarioId, List<ItemAviso> itens) {
        LocalDateTime agora = LocalDateTime.now(relogio);
        Map<String, List<ItemAviso>> porProduto = new LinkedHashMap<>();
        for (ItemAviso item : itens) {
            porProduto.computeIfAbsent(item.produtoId(), id -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<String, List<ItemAviso>> entrada : porProduto.entrySet()) {
            try {
                Produto produto = produtoService.buscarPorId(usuarioId, entrada.getKey());
                for (ItemAviso item : entrada.getValue()) {
                    produto.buscarLote(item.loteId())
                            .filter(Lote::isAtivo)
                            .filter(lote -> lote.getDataValidade().equals(item.dataValidade()))
                            .ifPresent(lote -> {
                                if (item.vencido()) {
                                    lote.setAvisoVencidoEm(agora);
                                } else {
                                    lote.setAvisoVencendoEm(agora);
                                }
                            });
                }
                produtoRepository.save(produto);
            } catch (ValidacaoException | OptimisticLockingFailureException e) {
                LOG.warn("Não foi possível marcar os avisos do produto {}: {}", entrada.getKey(), e.getMessage());
            }
        }
    }
}
