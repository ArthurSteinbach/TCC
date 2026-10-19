package br.com.unisinos.despensa.seguranca;

import br.com.unisinos.despensa.repository.UsuarioRepository;
import br.com.unisinos.despensa.service.UsuarioService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return usuarioRepository.findByEmail(UsuarioService.normalizarEmail(email))
                .map(UsuarioLogado::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado."));
    }
}
