package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.Usuario;
import br.com.unisinos.despensa.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioServiceTest {

    private static final String SENHA = "senha-segura-123";

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordEncoder codificador = new BCryptPasswordEncoder();
    private final UsuarioService usuarioService = new UsuarioService(usuarioRepository, codificador);

    @BeforeEach
    void configurarRepositorio() {
        when(usuarioRepository.existsByEmail(anyString())).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void cadastroValido_deveGuardarASenhaComHashBCryptENuncaEmTexto() {
        usuarioService.cadastrar("Ana", "ana@exemplo.com", SENHA, SENHA);

        Usuario salvo = usuarioSalvo();
        assertNotEquals(SENHA, salvo.getSenhaHash());
        assertTrue(salvo.getSenhaHash().startsWith("$2"));
        assertTrue(codificador.matches(SENHA, salvo.getSenhaHash()));
    }

    @Test
    void cadastro_deveNormalizarOEmailETirarEspacosDoNome() {
        usuarioService.cadastrar("  Ana Souza ", "  Ana@Exemplo.COM ", SENHA, SENHA);

        Usuario salvo = usuarioSalvo();
        assertEquals("Ana Souza", salvo.getNome());
        assertEquals("ana@exemplo.com", salvo.getEmail());
    }

    @Test
    void cadastroComEmailRepetido_deveSerRecusado() {
        when(usuarioRepository.existsByEmail("ana@exemplo.com")).thenReturn(true);

        ValidacaoException erro = assertThrows(ValidacaoException.class,
                () -> usuarioService.cadastrar("Ana", "ANA@exemplo.com", SENHA, SENHA));

        assertTrue(erro.getMessage().contains("e-mail"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cadastroComEmailRepetidoBarradoPeloIndiceUnico_deveSerRecusado() {
        when(usuarioRepository.save(any(Usuario.class))).thenThrow(new DuplicateKeyException("email"));

        assertThrows(ValidacaoException.class, () -> usuarioService.cadastrar("Ana", "ana@exemplo.com", SENHA, SENHA));
    }

    @Test
    void cadastroComSenhaCurta_deveSerRecusado() {
        ValidacaoException erro = assertThrows(ValidacaoException.class,
                () -> usuarioService.cadastrar("Ana", "ana@exemplo.com", "1234567", "1234567"));

        assertTrue(erro.getMessage().contains("8 caracteres"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cadastroComSenhaDeOitoCaracteres_deveSerAceito() {
        usuarioService.cadastrar("Ana", "ana@exemplo.com", "12345678", "12345678");

        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void cadastroComSenhaAcimaDoLimiteDoBCrypt_deveSerRecusado() {
        String senhaLonga = "a".repeat(73);

        assertThrows(ValidacaoException.class,
                () -> usuarioService.cadastrar("Ana", "ana@exemplo.com", senhaLonga, senhaLonga));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cadastroComConfirmacaoDiferente_deveSerRecusado() {
        assertThrows(ValidacaoException.class,
                () -> usuarioService.cadastrar("Ana", "ana@exemplo.com", SENHA, SENHA + "x"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cadastroSemNome_deveSerRecusado() {
        assertThrows(ValidacaoException.class, () -> usuarioService.cadastrar(" ", "ana@exemplo.com", SENHA, SENHA));
    }

    @Test
    void cadastroComEmailInvalido_deveSerRecusado() {
        assertThrows(ValidacaoException.class, () -> usuarioService.cadastrar("Ana", "ana.exemplo.com", SENHA, SENHA));
        assertThrows(ValidacaoException.class, () -> usuarioService.cadastrar("Ana", "  ", SENHA, SENHA));
    }

    private Usuario usuarioSalvo() {
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        return captor.getValue();
    }
}
