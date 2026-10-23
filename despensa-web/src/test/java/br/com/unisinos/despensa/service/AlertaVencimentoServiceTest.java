package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.email.EnviadorDeEmailFalso;
import br.com.unisinos.despensa.email.MensagemEmail;
import br.com.unisinos.despensa.email.MontadorDeEmails;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.model.ResultadoEnvioAlertas;
import br.com.unisinos.despensa.model.Usuario;
import br.com.unisinos.despensa.repository.UsuarioRepositoryEmMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static br.com.unisinos.despensa.service.AmbienteDeTeste.HOJE;
import static br.com.unisinos.despensa.service.AmbienteDeTeste.OUTRO_USUARIO;
import static br.com.unisinos.despensa.service.AmbienteDeTeste.USUARIO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertaVencimentoServiceTest {

    private static final String EMAIL_ANA = "ana@exemplo.com";
    private static final String EMAIL_BRUNO = "bruno@exemplo.com";

    private final AmbienteDeTeste ambiente = new AmbienteDeTeste();
    private final UsuarioRepositoryEmMemoria usuarios = new UsuarioRepositoryEmMemoria();
    private final EnviadorDeEmailFalso emails = new EnviadorDeEmailFalso();
    private final MontadorDeEmails montador = new MontadorDeEmails("https://despensa.teste");
    private final AlertaVencimentoService servico = servicoNoDia(0);

    private Produto leite;

    @BeforeEach
    void cadastrarUsuarios() {
        usuarios.cadastrar(USUARIO, "Ana", EMAIL_ANA);
        usuarios.cadastrar(OUTRO_USUARIO, "Bruno", EMAIL_BRUNO);
        leite = ambiente.produtoService.cadastrar(USUARIO, "111", "Leite Integral", null);
    }

    @Test
    void loteQueEntrouNaJanelaDe7Dias_deveGerarUmEmailNaSecaoVencendoEMarcarOLote() {
        Lote lote = ambiente.loteService.cadastrar(USUARIO, leite.getId(), 2, 4.0, HOJE.plusDays(3));

        ResultadoEnvioAlertas resultado = servico.executar().orElseThrow();

        assertEquals(new ResultadoEnvioAlertas(2, 1, 0), resultado);
        MensagemEmail email = emails.ultima();
        assertEquals(EMAIL_ANA, email.destinatario());
        assertEquals("Despensa: 1 item vencendo em breve", email.assunto());
        assertTrue(email.html().contains("Vencendo em breve"));
        assertFalse(email.html().contains(">Vencidos<"));
        assertTrue(email.html().contains("Leite Integral"));
        assertTrue(email.html().contains("21/10/2026"));
        assertTrue(email.html().contains("Vence em 3 dias"));
        assertTrue(email.texto().contains("- Leite Integral: 2 un., validade 21/10/2026 (Vence em 3 dias)"));
        assertNotNull(ambiente.lote(USUARIO, leite, lote.getId()).getAvisoVencendoEm());
        assertNull(ambiente.lote(USUARIO, leite, lote.getId()).getAvisoVencidoEm());
    }

    @Test
    void loteVencido_deveIrNaSecaoVencidos() {
        Lote lote = ambiente.loteVencido(USUARIO, leite, 1, 4.0, 2);

        servico.executar();

        MensagemEmail email = emails.ultima();
        assertEquals("Despensa: 1 item vencido", email.assunto());
        assertTrue(email.html().contains(">Vencidos<"));
        assertTrue(email.html().contains("Venceu há 2 dias"));
        assertTrue(email.texto().contains("VENCIDOS"));
        assertNotNull(ambiente.lote(USUARIO, leite, lote.getId()).getAvisoVencidoEm());
    }

    @Test
    void loteNoUltimoDiaDaJanela_entraEForaDaJanela_naoEntra() {
        ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(8));

        servico.executar();
        assertTrue(emails.enviadas().isEmpty());

        ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(7));
        servico.executar();
        assertEquals(1, emails.enviadas().size());
        assertTrue(emails.ultima().html().contains("Vence em 7 dias"));
        assertFalse(emails.ultima().html().contains("Vence em 8 dias"));
    }

    @Test
    void variosLotesNovos_deveMandarUmUnicoEmailComAsDuasSecoes() {
        Produto iogurte = ambiente.produtoService.cadastrar(USUARIO, "222", "Iogurte", null);
        ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(1));
        ambiente.loteService.cadastrar(USUARIO, iogurte.getId(), 3, 2.0, HOJE);
        ambiente.loteVencido(USUARIO, iogurte, 2, 2.0, 1);

        servico.executar();

        assertEquals(1, emails.enviadas().size());
        MensagemEmail email = emails.ultima();
        assertEquals("Despensa: 1 item vencido e 2 itens vencendo em breve", email.assunto());
        assertTrue(email.html().indexOf(">Vencidos<") < email.html().indexOf("Vencendo em breve"));
        assertTrue(email.html().contains("Vence hoje") && email.html().contains("Vence amanhã"));
    }

    @Test
    void segundaExecucaoNoMesmoDia_naoDeveRepetirNemComNovidade() {
        ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(2));
        servico.executar();
        ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(4));

        ResultadoEnvioAlertas segunda = servico.executar().orElseThrow();

        assertEquals(0, segunda.emailsEnviados());
        assertEquals(1, emails.enviadas().size());

        servicoNoDia(1).executar();
        assertEquals(2, emails.enviadas().size());
        assertTrue(emails.ultima().html().contains("22/10/2026"));
        assertFalse(emails.ultima().html().contains("20/10/2026"));
    }

    @Test
    void diaSeguinteSemNovidade_naoDeveEnviar() {
        ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(5));
        servico.executar();

        ResultadoEnvioAlertas dia2 = servicoNoDia(1).executar().orElseThrow();

        assertEquals(0, dia2.emailsEnviados());
        assertEquals(1, emails.enviadas().size());
    }

    @Test
    void loteJaAvisadoComoVencendo_deveSerAvisadoDeNovoQuandoVencer() {
        Lote lote = ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(1));
        servico.executar();
        assertTrue(emails.ultima().html().contains("Vencendo em breve"));

        servicoNoDia(1).executar();
        assertEquals(1, emails.enviadas().size());

        servicoNoDia(2).executar();
        assertEquals(2, emails.enviadas().size());
        assertTrue(emails.ultima().html().contains(">Vencidos<"));
        assertTrue(emails.ultima().html().contains("Venceu há 1 dia"));
        assertNotNull(ambiente.lote(USUARIO, leite, lote.getId()).getAvisoVencidoEm());

        servicoNoDia(3).executar();
        assertEquals(2, emails.enviadas().size());
    }

    @Test
    void falhaNoEnvio_naoDeveMarcarOsLotesNemODiaParaTentarDeNovo() {
        Lote lote = ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(2));
        emails.falharEnvios(true);

        ResultadoEnvioAlertas resultado = servico.executar().orElseThrow();

        assertEquals(1, resultado.falhas());
        assertEquals(0, resultado.emailsEnviados());
        assertNull(ambiente.lote(USUARIO, leite, lote.getId()).getAvisoVencendoEm());
        assertNull(usuarios.findById(USUARIO).orElseThrow().getUltimoEnvioAlertas());

        emails.falharEnvios(false);
        servico.executar();
        assertEquals(1, emails.enviadas().size());
        assertNotNull(ambiente.lote(USUARIO, leite, lote.getId()).getAvisoVencendoEm());
    }

    @Test
    void editarAValidadeDoLote_deveZerarOsAvisosEAvisarDeNovo() {
        Lote lote = ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(2));
        servico.executar();
        assertNotNull(ambiente.lote(USUARIO, leite, lote.getId()).getAvisoVencendoEm());

        ambiente.loteService.editar(USUARIO, leite.getId(), lote.getId(), 1, 4.0, HOJE.plusDays(5));
        assertNull(ambiente.lote(USUARIO, leite, lote.getId()).getAvisoVencendoEm());

        servicoNoDia(1).executar();
        assertEquals(2, emails.enviadas().size());
        assertTrue(emails.ultima().html().contains("23/10/2026"));
    }

    @Test
    void usuarioComAvisosDesligados_naoDeveReceber() {
        Usuario ana = usuarios.findById(USUARIO).orElseThrow();
        ana.setAvisosAtivos(false);
        usuarios.save(ana);
        ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(2));

        servico.executar();

        assertTrue(emails.enviadas().isEmpty());
    }

    @Test
    void cadaUsuario_deveReceberSoOsProprioLotesNoProprioEmail() {
        ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(2));
        Produto cafe = ambiente.produtoService.cadastrar(OUTRO_USUARIO, "333", "Café do Bruno", null);
        ambiente.loteService.cadastrar(OUTRO_USUARIO, cafe.getId(), 1, 20.0, HOJE.plusDays(4));

        ResultadoEnvioAlertas resultado = servico.executar().orElseThrow();

        assertEquals(2, resultado.emailsEnviados());
        MensagemEmail paraAna = emails.enviadasPara(EMAIL_ANA).get(0);
        MensagemEmail paraBruno = emails.enviadasPara(EMAIL_BRUNO).get(0);
        assertTrue(paraAna.html().contains("Leite Integral") && !paraAna.html().contains("Café do Bruno"));
        assertTrue(paraBruno.html().contains("Café do Bruno") && !paraBruno.html().contains("Leite Integral"));
    }

    @Test
    void emailConfirmadoDosAvisos_deveSerUsadoEPendenteNao() {
        Usuario ana = usuarios.findById(USUARIO).orElseThrow();
        ana.setEmailAvisos("avisos@exemplo.com");
        ana.setEmailAvisosPendente("pendente@exemplo.com");
        usuarios.save(ana);
        ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(2));

        servico.executar();

        assertEquals("avisos@exemplo.com", emails.ultima().destinatario());
    }

    @Test
    void email_deveEscaparONomeDoProdutoETerOsLinksDeAlertasEDescadastro() {
        Produto perigoso = ambiente.produtoService.cadastrar(USUARIO, "999", "<script>alert(1)</script> Doce", null);
        ambiente.loteService.cadastrar(USUARIO, perigoso.getId(), 1, 4.0, HOJE.plusDays(2));

        servico.executar();

        MensagemEmail email = emails.ultima();
        String token = usuarios.findById(USUARIO).orElseThrow().getTokenDescadastro();
        assertNotNull(token);
        assertFalse(email.html().contains("<script>"));
        assertTrue(email.html().contains("&lt;script&gt;"));
        assertTrue(email.html().contains("href=\"https://despensa.teste/alertas\""));
        assertTrue(email.html().contains("https://despensa.teste/notificacoes/descadastrar?token=" + token));
        assertTrue(email.texto().contains("https://despensa.teste/alertas"));
        assertTrue(email.texto().contains("https://despensa.teste/notificacoes/descadastrar?token=" + token));
    }

    @Test
    void lotesConsumidosOuDescartados_naoDevemGerarAviso() {
        Lote consumido = ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(2));
        Lote descartado = ambiente.loteVencido(USUARIO, leite, 1, 4.0, 1);
        ambiente.loteService.consumir(USUARIO, leite.getId(), consumido.getId());
        ambiente.loteService.descartar(USUARIO, leite.getId(), descartado.getId());

        servico.executar();

        assertTrue(emails.enviadas().isEmpty());
    }

    @Test
    void chamadaEnquantoOutraExecucaoEstaEmAndamento_deveSerRecusada() {
        ambiente.loteService.cadastrar(USUARIO, leite.getId(), 1, 4.0, HOJE.plusDays(2));
        AtomicReference<Optional<ResultadoEnvioAlertas>> concorrente = new AtomicReference<>();
        emails.executarAntesDoProximoEnvio(() -> concorrente.set(servico.executar()));

        servico.executar();

        assertTrue(concorrente.get().isEmpty());
        assertEquals(1, emails.enviadas().size());
    }

    private AlertaVencimentoService servicoNoDia(int diasDepois) {
        Clock relogio = Clock.offset(ambiente.relogio, Duration.ofDays(diasDepois));
        ProdutoService produtoService = new ProdutoService(ambiente.repositorio, relogio);
        return new AlertaVencimentoService(usuarios, produtoService, ambiente.repositorio, emails, montador, relogio);
    }
}
