package br.com.unisinos.despensa.seguranca;

import br.com.unisinos.despensa.model.Usuario;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UsuarioLogado implements UserDetails, CredentialsContainer {

    private static final List<GrantedAuthority> PERMISSOES = List.of(new SimpleGrantedAuthority("ROLE_USUARIO"));

    private final String id;
    private final String nome;
    private final String email;
    private String senhaHash;

    public UsuarioLogado(String id, String nome, String email, String senhaHash) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
    }

    public UsuarioLogado(Usuario usuario) {
        this(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getSenhaHash());
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return PERMISSOES;
    }

    @Override
    public void eraseCredentials() {
        senhaHash = null;
    }
}
