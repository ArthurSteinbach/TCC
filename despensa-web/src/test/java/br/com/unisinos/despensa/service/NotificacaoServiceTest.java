package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.email.EnviadorDeEmailFalso;
import br.com.unisinos.despensa.email.FalhaNoEnvioException;
import br.com.unisinos.despensa.email.MensagemEmail;
import br.com.unisinos.despensa.email.MontadorDeEmails;
import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.PreferenciasNotificacao;
import br.com.unisinos.despensa.model.Usuario;
import br.com.unisinos.despensa.repository.UsuarioRepositoryEmMemoria;
import br.com.unisinos.despensa.util.Constantes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificacaoServiceTest {

    private static final String ID = "usuario-ana";
    private static final Instant AGORA = Instant.parse("2026-10-19T01:30:00Z");
    private static final Pattern TOKEN_NO_LINK = Pattern.compile("confirmar\\?token=([A-Za-z0-9_-]+)");

    private final UsuarioRepositoryEmMemoria usuarios = new UsuarioRepositoryEmMemoria();
    private final EnviadorDeEmailFalso emails = new EnviadorDeEmailFalso();
    private final MontadorDeEmails montador = new MontadorDeEmails("https://despensa.teste");

    @BeforeEach
    void cadastrarUsuario() {
        usuarios.cadastrar(ID, "Ana", "ana@exemplo.com");
    }

    @Test
    void contaNovaOuExistente_deveTerAvisosLigadosParaOEmailDaConta() {
        PreferenciasNotificacao preferencias = servico(0).preferencias(ID);

        assertTrue(preferencias.avisosAtivos());
        assertEquals("ana@exemplo.com", preferencias.emailDestino());
        assertTrue(preferencias.usaEmailDaConta());
        assertFalse(preferencias.temPendente());
    }

    @Test
    void desligarELigarOsAvisos_deveFicarGuardado() {
        servico(0).definirAvisos(ID, false);
        assertFalse(servico(0).preferencias(ID).avisosAtivos());

        servico(0).definirAvisos(ID, true);
        assertTrue(servico(0).preferencias(ID).avisosAtivos());
    }

    @Test
    void novoEmail_deveReceberOLinkDeConfirmacaoSemMudarODestinoAntesDeConfirmar() throws Exception {
        assertTrue(servico(0).solicitarTrocaDeEmail(ID, "  Novo@Exemplo.com "));

        MensagemEmail confirmacao = emails.ultima();
        assertEquals("novo@exemplo.com", confirmacao.destinatario());
        assertTrue(confirmacao.html().contains("https://despensa.teste/notificacoes/confirmar?token="));
        PreferenciasNotificacao preferencias = servico(0).preferencias(ID);
        assertEquals("ana@exemplo.com", preferencias.emailDestino());
        assertEquals("novo@exemplo.com", preferencias.emailPendente());
        Usuario guardado = usuarios.findById(ID).orElseThrow();
        assertFalse(guardado.getHashTokenConfirmacao().equals(tokenDe(confirmacao)));
    }

    @Test
    void confirmarComLinkValido_deveMudarODestino() throws Exception {
        servico(0).solicitarTrocaDeEmail(ID, "novo@exemplo.com");
        String token = tokenDe(emails.ultima());

        String confirmado = servico(0).confirmarEmail(token);

        assertEquals("novo@exemplo.com", confirmado);
        PreferenciasNotificacao preferencias = servico(0).preferencias(ID);
        assertEquals("novo@exemplo.com", preferencias.emailDestino());
        assertFalse(preferencias.temPendente());
        assertThrows(ValidacaoException.class, () -> servico(0).confirmarEmail(token));
    }

    @Test
    void confirmarDepoisDe24Horas_deveSerRecusadoEManterOEmailAnterior() throws Exception {
        servico(0).solicitarTrocaDeEmail(ID, "novo@exemplo.com");
        String token = tokenDe(emails.ultima());

        ValidacaoException erro = assertThrows(ValidacaoException.class,
                () -> servicoDepois(Duration.ofHours(24).plusMinutes(1)).confirmarEmail(token));

        assertTrue(erro.getMessage().contains("expirou"));
        PreferenciasNotificacao preferencias = servico(0).preferencias(ID);
        assertEquals("ana@exemplo.com", preferencias.emailDestino());
        assertFalse(preferencias.temPendente());
    }

    @Test
    void confirmarPoucoAntesDe24Horas_deveFuncionar() throws Exception {
        servico(0).solicitarTrocaDeEmail(ID, "novo@exemplo.com");

        assertEquals("novo@exemplo.com",
                servicoDepois(Duration.ofHours(23).plusMinutes(59)).confirmarEmail(tokenDe(emails.ultima())));
    }

    @Test
    void tokenInvalidoOuVazio_deveSerRecusado() {
        assertThrows(ValidacaoException.class, () -> servico(0).confirmarEmail("token-inventado"));
        assertThrows(ValidacaoException.class, () -> servico(0).confirmarEmail(""));
        assertThrows(ValidacaoException.class, () -> servico(0).confirmarEmail(null));
    }

    @Test
    void voltarParaOEmailDaConta_naoPrecisaDeConfirmacao() throws Exception {
        Usuario ana = usuarios.findById(ID).orElseThrow();
        ana.setEmailAvisos("outro@exemplo.com");
        usuarios.save(ana);

        assertFalse(servico(0).solicitarTrocaDeEmail(ID, "ana@exemplo.com"));

        assertEquals("ana@exemplo.com", servico(0).preferencias(ID).emailDestino());
        assertTrue(emails.enviadas().isEmpty());
    }

    @Test
    void emailInvalidoOuIgualAoAtual_deveSerRecusado() {
        assertThrows(ValidacaoException.class, () -> servico(0).solicitarTrocaDeEmail(ID, "sem-arroba"));
        assertThrows(ValidacaoException.class, () -> servico(0).solicitarTrocaDeEmail(ID, "ana@exemplo.com"));
        assertTrue(emails.enviadas().isEmpty());
    }

    @Test
    void falhaAoEnviarAConfirmacao_naoDeveDeixarPendencia() {
        emails.falharEnvios(true);

        assertThrows(FalhaNoEnvioException.class, () -> servico(0).solicitarTrocaDeEmail(ID, "novo@exemplo.com"));

        assertFalse(servico(0).preferencias(ID).temPendente());
        assertNull(usuarios.findById(ID).orElseThrow().getHashTokenConfirmacao());
    }

    @Test
    void emailDeTeste_deveIrParaODestinoComLinkDeDescadastro() throws Exception {
        String destino = servico(0).enviarTeste(ID);

        assertEquals("ana@exemplo.com", destino);
        String token = usuarios.findById(ID).orElseThrow().getTokenDescadastro();
        assertTrue(emails.ultima().html().contains("descadastrar?token=" + token));
    }

    @Test
    void enviosManuais_devemRespeitarOIntervaloDeUmMinuto() throws Exception {
        servico(0).enviarTeste(ID);

        ValidacaoException erro = assertThrows(ValidacaoException.class,
                () -> servicoDepois(Duration.ofSeconds(30)).enviarTeste(ID));
        assertThrows(ValidacaoException.class,
                () -> servicoDepois(Duration.ofSeconds(30)).solicitarTrocaDeEmail(ID, "novo@exemplo.com"));

        assertTrue(erro.getMessage().contains("Aguarde"));
        servicoDepois(Duration.ofSeconds(61)).enviarTeste(ID);
        assertEquals(2, emails.enviadas().size());
    }

    @Test
    void envioNaoConfigurado_deveExplicarSemTentarEnviar() {
        emails.desligar();

        ValidacaoException erro = assertThrows(ValidacaoException.class, () -> servico(0).enviarTeste(ID));

        assertTrue(erro.getMessage().contains("não está configurado"));
        assertFalse(servico(0).preferencias(ID).envioConfigurado());
    }

    @Test
    void descadastroComTokenValido_deveDesligarOsAvisos() throws Exception {
        servico(0).enviarTeste(ID);
        String token = usuarios.findById(ID).orElseThrow().getTokenDescadastro();

        assertEquals("ana@exemplo.com", servico(0).emailDoDescadastro(token).orElseThrow());
        servico(0).descadastrar(token);

        assertFalse(servico(0).preferencias(ID).avisosAtivos());
    }

    @Test
    void descadastroComTokenInvalido_deveSerRecusado() {
        assertTrue(servico(0).emailDoDescadastro("inventado").isEmpty());
        assertThrows(ValidacaoException.class, () -> servico(0).descadastrar("inventado"));
        assertThrows(ValidacaoException.class, () -> servico(0).descadastrar(""));
        assertTrue(servico(0).preferencias(ID).avisosAtivos());
    }

    private NotificacaoService servico(long segundosDepois) {
        return servicoDepois(Duration.ofSeconds(segundosDepois));
    }

    private NotificacaoService servicoDepois(Duration tempo) {
        Clock relogio = Clock.fixed(AGORA.plus(tempo), Constantes.FUSO_HORARIO);
        return new NotificacaoService(usuarios, emails, montador, relogio);
    }

    private static String tokenDe(MensagemEmail mensagem) {
        Matcher token = TOKEN_NO_LINK.matcher(mensagem.texto());
        assertTrue(token.find(), "E-mail sem link de confirmação");
        return token.group(1);
    }
}
