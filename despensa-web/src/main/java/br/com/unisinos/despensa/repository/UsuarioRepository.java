package br.com.unisinos.despensa.repository;

import br.com.unisinos.despensa.model.Usuario;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends Repository<Usuario, String> {

    Usuario save(Usuario usuario);

    Optional<Usuario> findById(String id);

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Usuario> findByHashTokenConfirmacao(String hashTokenConfirmacao);

    Optional<Usuario> findByTokenDescadastro(String tokenDescadastro);

    List<Usuario> findAll();
}
