package br.com.unisinos.despensa.service;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.model.Usuario;
import br.com.unisinos.despensa.repository.UsuarioRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class UsuarioService {

    public static final int TAMANHO_MINIMO_SENHA = 8;
    public static final int TAMANHO_MAXIMO_SENHA_EM_BYTES = 72;

    private static final Pattern FORMATO_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String EMAIL_REPETIDO = "Já existe uma conta com este e-mail.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder codificadorDeSenha;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder codificadorDeSenha) {
        this.usuarioRepository = usuarioRepository;
        this.codificadorDeSenha = codificadorDeSenha;
    }

    public Usuario cadastrar(String nome, String email, String senha, String confirmacaoSenha) {
        if (nome == null || nome.isBlank()) {
            throw new ValidacaoException("O nome é obrigatório.");
        }
        if (email == null || email.isBlank()) {
            throw new ValidacaoException("O e-mail é obrigatório.");
        }
        String emailNormalizado = normalizarEmail(email);
        if (!emailValido(emailNormalizado)) {
            throw new ValidacaoException("Informe um e-mail válido.");
        }
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new ValidacaoException("A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > TAMANHO_MAXIMO_SENHA_EM_BYTES) {
            throw new ValidacaoException("A senha é longa demais (máximo de 72 caracteres sem acentos).");
        }
        if (!senha.equals(confirmacaoSenha)) {
            throw new ValidacaoException("A confirmação da senha não confere.");
        }
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new ValidacaoException(EMAIL_REPETIDO);
        }

        Usuario usuario = new Usuario(nome.trim(), emailNormalizado, codificadorDeSenha.encode(senha));
        try {
            return usuarioRepository.save(usuario);
        } catch (DuplicateKeyException e) {
            throw new ValidacaoException(EMAIL_REPETIDO);
        }
    }

    public static boolean emailValido(String emailNormalizado) {
        return emailNormalizado != null && FORMATO_EMAIL.matcher(emailNormalizado).matches();
    }

    public static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
