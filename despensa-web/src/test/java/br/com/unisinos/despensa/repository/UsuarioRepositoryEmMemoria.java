package br.com.unisinos.despensa.repository;

import br.com.unisinos.despensa.model.Usuario;
import org.bson.types.ObjectId;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public class UsuarioRepositoryEmMemoria implements UsuarioRepository {

    private final Map<String, Usuario> documentos = new LinkedHashMap<>();
    private boolean bancoForaDoAr;

    @Override
    public Usuario save(Usuario usuario) {
        verificarBanco();
        boolean emailRepetido = documentos.values().stream()
                .anyMatch(u -> !u.getId().equals(usuario.getId()) && u.getEmail().equals(usuario.getEmail()));
        if (emailRepetido) {
            throw new DuplicateKeyException("email");
        }
        if (usuario.getId() == null) {
            usuario.setId(new ObjectId().toHexString());
        }
        documentos.put(usuario.getId(), copiar(usuario));
        return usuario;
    }

    @Override
    public Optional<Usuario> findById(String id) {
        verificarBanco();
        return Optional.ofNullable(documentos.get(id)).map(UsuarioRepositoryEmMemoria::copiar);
    }

    @Override
    public Optional<Usuario> findByEmail(String email) {
        return buscar(u -> u.getEmail().equals(email));
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    @Override
    public Optional<Usuario> findByHashTokenConfirmacao(String hashTokenConfirmacao) {
        return buscar(u -> Objects.equals(u.getHashTokenConfirmacao(), hashTokenConfirmacao));
    }

    @Override
    public Optional<Usuario> findByTokenDescadastro(String tokenDescadastro) {
        return buscar(u -> Objects.equals(u.getTokenDescadastro(), tokenDescadastro));
    }

    @Override
    public List<Usuario> findAll() {
        verificarBanco();
        return documentos.values().stream().map(UsuarioRepositoryEmMemoria::copiar).toList();
    }

    public Usuario cadastrar(String id, String nome, String email) {
        Usuario usuario = new Usuario(nome, email, null);
        usuario.setId(id);
        return save(usuario);
    }

    public int quantidade() {
        return documentos.size();
    }

    public void simularBancoForaDoAr(boolean foraDoAr) {
        bancoForaDoAr = foraDoAr;
    }

    public void limpar() {
        documentos.clear();
        bancoForaDoAr = false;
    }

    private Optional<Usuario> buscar(Predicate<Usuario> filtro) {
        verificarBanco();
        return documentos.values().stream().filter(filtro).findFirst().map(UsuarioRepositoryEmMemoria::copiar);
    }

    private void verificarBanco() {
        if (bancoForaDoAr) {
            throw new DataAccessResourceFailureException("Timed out while waiting for a server");
        }
    }

    private static Usuario copiar(Usuario origem) {
        Usuario copia = new Usuario(origem.getNome(), origem.getEmail(), origem.getSenhaHash());
        copia.setId(origem.getId());
        copia.setAvisosAtivos(origem.getAvisosAtivos());
        copia.setEmailAvisos(origem.getEmailAvisos());
        copia.setEmailAvisosPendente(origem.getEmailAvisosPendente());
        copia.setHashTokenConfirmacao(origem.getHashTokenConfirmacao());
        copia.setConfirmacaoExpiraEm(origem.getConfirmacaoExpiraEm());
        copia.setTokenDescadastro(origem.getTokenDescadastro());
        copia.setUltimoEnvioAlertas(origem.getUltimoEnvioAlertas());
        copia.setUltimoEnvioManual(origem.getUltimoEnvioManual());
        return copia;
    }
}
