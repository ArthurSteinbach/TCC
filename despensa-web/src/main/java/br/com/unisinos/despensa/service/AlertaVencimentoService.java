package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.email.EnviadorDeEmail;
import br.com.unisinos.despensa.email.FalhaNoEnvioException;
import br.com.unisinos.despensa.email.ItemAviso;
import br.com.unisinos.despensa.email.MontadorDeEmails;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.ResultadoEnvioAlertas;
import br.com.unisinos.despensa.model.Usuario;
import br.com.unisinos.despensa.repository.UsuarioRepository;
import br.com.unisinos.despensa.util.Constantes;
import br.com.unisinos.despensa.util.Tokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AlertaVencimentoService {

    private static final Logger LOG = LoggerFactory.getLogger(AlertaVencimentoService.class);

    private final UsuarioRepository usuarioRepository;
    private final ProdutoService produtoService;
    private final EnviadorDeEmail enviador;
    private final MontadorDeEmails montador;
    private final Clock relogio;
    private final AtomicBoolean emExecucao = new AtomicBoolean();

    public AlertaVencimentoService(UsuarioRepository usuarioRepository, ProdutoService produtoService,
                                   EnviadorDeEmail enviador, MontadorDeEmails montador, Clock relogio) {
        this.usuarioRepository = usuarioRepository;
        this.produtoService = produtoService;
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

    private List<ItemAviso> itensQuePrecisamDeAtencao(String usuarioId, LocalDate hoje) {
        LocalDate limite = hoje.plusDays(Constantes.DIAS_ALERTA_VENCIMENTO);
        List<ItemAviso> itens = new ArrayList<>();
        for (Produto produto : produtoService.listar(usuarioId)) {
            for (Lote lote : produto.getLotes()) {
                if (lote.isAtivo() && !lote.getDataValidade().isAfter(limite)) {
                    itens.add(new ItemAviso(produto.getNome(), lote.getQuantidade(), lote.getDataValidade(),
                            ChronoUnit.DAYS.between(hoje, lote.getDataValidade())));
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
        List<ItemAviso> itens = itensQuePrecisamDeAtencao(usuario.getId(), hoje);
        if (itens.isEmpty()) {
            return false;
        }

        Usuario destinatario = usuario;
        if (destinatario.getTokenDescadastro() == null) {
            destinatario.setTokenDescadastro(Tokens.novo());
            destinatario = usuarioRepository.save(destinatario);
        }

        List<ItemAviso> vencidos = itens.stream().filter(ItemAviso::vencido).toList();
        List<ItemAviso> vencendo = itens.stream().filter(item -> !item.vencido()).toList();
        enviador.enviar(montador.alertas(destinatario.getNome(), destinatario.emailDeDestinoDosAvisos(), vencidos,
                vencendo, destinatario.getTokenDescadastro(), hoje));

        destinatario.setUltimoEnvioAlertas(hoje);
        usuarioRepository.save(destinatario);
        return true;
    }
}
