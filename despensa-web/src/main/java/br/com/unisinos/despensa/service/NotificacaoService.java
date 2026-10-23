package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.email.EnviadorDeEmail;
import br.com.unisinos.despensa.email.FalhaNoEnvioException;
import br.com.unisinos.despensa.email.MontadorDeEmails;
import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.PreferenciasNotificacao;
import br.com.unisinos.despensa.model.Usuario;
import br.com.unisinos.despensa.repository.UsuarioRepository;
import br.com.unisinos.despensa.util.Tokens;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class NotificacaoService {

    public static final Duration VALIDADE_DA_CONFIRMACAO = Duration.ofHours(24);
    public static final Duration INTERVALO_ENTRE_ENVIOS_MANUAIS = Duration.ofMinutes(1);

    private static final String ENVIO_NAO_CONFIGURADO = "O envio de e-mails não está configurado no servidor.";

    private final UsuarioRepository usuarioRepository;
    private final EnviadorDeEmail enviador;
    private final MontadorDeEmails montador;
    private final Clock relogio;

    public NotificacaoService(UsuarioRepository usuarioRepository, EnviadorDeEmail enviador,
                              MontadorDeEmails montador, Clock relogio) {
        this.usuarioRepository = usuarioRepository;
        this.enviador = enviador;
        this.montador = montador;
        this.relogio = relogio;
    }

    public PreferenciasNotificacao preferencias(String usuarioId) {
        Usuario usuario = buscar(usuarioId);
        boolean pendenteValido = usuario.getEmailAvisosPendente() != null
                && usuario.getConfirmacaoExpiraEm() != null && usuario.getConfirmacaoExpiraEm().isAfter(agora());
        return new PreferenciasNotificacao(usuario.recebeAvisos(), usuario.getEmail(),
                usuario.emailDeDestinoDosAvisos(), pendenteValido ? usuario.getEmailAvisosPendente() : null,
                pendenteValido ? usuario.getConfirmacaoExpiraEm() : null, enviador.ativo());
    }

    public void definirAvisos(String usuarioId, boolean ativos) {
        Usuario usuario = buscar(usuarioId);
        usuario.setAvisosAtivos(ativos);
        usuarioRepository.save(usuario);
    }

    public boolean solicitarTrocaDeEmail(String usuarioId, String email) throws FalhaNoEnvioException {
        Usuario usuario = buscar(usuarioId);
        String novoEmail = UsuarioService.normalizarEmail(email);
        if (!UsuarioService.emailValido(novoEmail)) {
            throw new ValidacaoException("Informe um e-mail válido.");
        }
        if (novoEmail.equals(usuario.emailDeDestinoDosAvisos())) {
            throw new ValidacaoException("Os avisos já vão para este e-mail.");
        }
        if (novoEmail.equals(usuario.getEmail())) {
            usuario.setEmailAvisos(null);
            usuario.limparConfirmacaoPendente();
            usuarioRepository.save(usuario);
            return false;
        }
        if (!enviador.ativo()) {
            throw new ValidacaoException(ENVIO_NAO_CONFIGURADO);
        }
        registrarEnvioManual(usuario);

        String token = Tokens.novo();
        LocalDateTime expiraEm = agora().plus(VALIDADE_DA_CONFIRMACAO);
        usuario.setEmailAvisosPendente(novoEmail);
        usuario.setHashTokenConfirmacao(Tokens.hash(token));
        usuario.setConfirmacaoExpiraEm(expiraEm);
        usuarioRepository.save(usuario);

        try {
            enviador.enviar(montador.confirmacao(usuario.getNome(), novoEmail, token, expiraEm));
        } catch (FalhaNoEnvioException e) {
            usuario.limparConfirmacaoPendente();
            usuarioRepository.save(usuario);
            throw e;
        }
        return true;
    }

    public String confirmarEmail(String token) {
        Usuario usuario = Optional.ofNullable(token).filter(t -> !t.isBlank())
                .flatMap(t -> usuarioRepository.findByHashTokenConfirmacao(Tokens.hash(t)))
                .orElseThrow(() -> new ValidacaoException("Este link de confirmação é inválido ou já foi usado."));
        if (usuario.getConfirmacaoExpiraEm() == null || !usuario.getConfirmacaoExpiraEm().isAfter(agora())) {
            usuario.limparConfirmacaoPendente();
            usuarioRepository.save(usuario);
            throw new ValidacaoException("Este link expirou. Peça um novo na tela Notificações.");
        }
        String confirmado = usuario.getEmailAvisosPendente();
        usuario.setEmailAvisos(confirmado);
        usuario.limparConfirmacaoPendente();
        usuarioRepository.save(usuario);
        return confirmado;
    }

    public void usarEmailDaConta(String usuarioId) {
        Usuario usuario = buscar(usuarioId);
        usuario.setEmailAvisos(null);
        usuario.limparConfirmacaoPendente();
        usuarioRepository.save(usuario);
    }

    public String enviarTeste(String usuarioId) throws FalhaNoEnvioException {
        Usuario usuario = buscar(usuarioId);
        if (!enviador.ativo()) {
            throw new ValidacaoException(ENVIO_NAO_CONFIGURADO);
        }
        registrarEnvioManual(usuario);
        if (usuario.getTokenDescadastro() == null) {
            usuario.setTokenDescadastro(Tokens.novo());
        }
        usuarioRepository.save(usuario);
        String destino = usuario.emailDeDestinoDosAvisos();
        enviador.enviar(montador.teste(usuario.getNome(), destino, usuario.getTokenDescadastro()));
        return destino;
    }

    public Optional<String> emailDoDescadastro(String token) {
        return buscarPorTokenDescadastro(token).map(Usuario::emailDeDestinoDosAvisos);
    }

    public void descadastrar(String token) {
        Usuario usuario = buscarPorTokenDescadastro(token)
                .orElseThrow(() -> new ValidacaoException("Este link de descadastro é inválido."));
        usuario.setAvisosAtivos(false);
        usuarioRepository.save(usuario);
    }

    private Optional<Usuario> buscarPorTokenDescadastro(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return usuarioRepository.findByTokenDescadastro(token);
    }

    private void registrarEnvioManual(Usuario usuario) {
        LocalDateTime agora = agora();
        LocalDateTime ultimo = usuario.getUltimoEnvioManual();
        if (ultimo != null && ultimo.plus(INTERVALO_ENTRE_ENVIOS_MANUAIS).isAfter(agora)) {
            throw new ValidacaoException("Aguarde um minuto antes de enviar outro e-mail.");
        }
        usuario.setUltimoEnvioManual(agora);
    }

    private Usuario buscar(String usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ValidacaoException("Usuário não encontrado."));
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(relogio);
    }
}
