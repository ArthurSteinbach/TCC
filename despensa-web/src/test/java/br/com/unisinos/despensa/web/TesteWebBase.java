package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.email.EnviadorDeEmail;
import br.com.unisinos.despensa.email.EnviadorDeEmailFalso;
import br.com.unisinos.despensa.integracao.ConsultaProdutoExterna;
import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.Produto;
import br.com.unisinos.despensa.repository.ProdutoRepository;
import br.com.unisinos.despensa.repository.ProdutoRepositoryEmMemoria;
import br.com.unisinos.despensa.repository.UsuarioRepository;
import br.com.unisinos.despensa.repository.UsuarioRepositoryEmMemoria;
import br.com.unisinos.despensa.seguranca.UsuarioLogado;
import br.com.unisinos.despensa.service.LoteService;
import br.com.unisinos.despensa.service.ProdutoService;
import br.com.unisinos.despensa.util.Constantes;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://localhost:27017",
        "despensa.indices.criar-ao-iniciar=false",
        "despensa.alertas.token=token-de-teste-com-tamanho-razoavel",
        "despensa.url-publica=https://despensa.teste"
})
@AutoConfigureMockMvc
abstract class TesteWebBase {

    static final LocalDate HOJE = LocalDate.of(2026, 10, 18);

    static final UsuarioLogado ANA = new UsuarioLogado("usuario-ana", "Ana", "ana@exemplo.com", null);
    static final UsuarioLogado BRUNO = new UsuarioLogado("usuario-bruno", "Bruno", "bruno@exemplo.com", null);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ProdutoService produtoService;

    @Autowired
    protected LoteService loteService;

    @TestBean
    protected ProdutoRepository produtoRepository;

    @TestBean
    protected Clock relogio;

    @TestBean
    protected UsuarioRepository usuarioRepository;

    @TestBean
    protected EnviadorDeEmail enviadorDeEmail;

    @MockitoBean
    protected ConsultaProdutoExterna consultaProdutoExterna;

    static ProdutoRepository produtoRepository() {
        return new ProdutoRepositoryEmMemoria();
    }

    static UsuarioRepository usuarioRepository() {
        return new UsuarioRepositoryEmMemoria();
    }

    static EnviadorDeEmail enviadorDeEmail() {
        return new EnviadorDeEmailFalso();
    }

    static Clock relogio() {
        return Clock.fixed(Instant.parse("2026-10-19T01:30:00Z"), Constantes.FUSO_HORARIO);
    }

    @BeforeEach
    void limparDados() {
        repositorio().limpar();
        usuarios().limpar();
        emails().limpar();
        usuarios().cadastrar(ANA.getId(), ANA.getNome(), ANA.getEmail());
        usuarios().cadastrar(BRUNO.getId(), BRUNO.getNome(), BRUNO.getEmail());
        Mockito.reset(consultaProdutoExterna);
    }

    protected UsuarioRepositoryEmMemoria usuarios() {
        return (UsuarioRepositoryEmMemoria) usuarioRepository;
    }

    protected EnviadorDeEmailFalso emails() {
        return (EnviadorDeEmailFalso) enviadorDeEmail;
    }

    protected ProdutoRepositoryEmMemoria repositorio() {
        return (ProdutoRepositoryEmMemoria) produtoRepository;
    }

    protected Lote lote(UsuarioLogado usuario, Produto produto, int quantidade, double preco, LocalDate validade) {
        return loteService.cadastrar(usuario.getId(), produto.getId(), quantidade, preco, validade);
    }

    protected Lote loteVencido(UsuarioLogado usuario, Produto produto, int quantidade, double preco, int diasVencido) {
        Lote lote = lote(usuario, produto, quantidade, preco, HOJE.plusDays(1));
        return loteService.editar(usuario.getId(), produto.getId(), lote.getId(), quantidade, preco,
                HOJE.minusDays(diasVencido));
    }

    protected Lote recarregar(UsuarioLogado usuario, Produto produto, Lote lote) {
        return produtoService.buscarPorId(usuario.getId(), produto.getId()).buscarLote(lote.getId()).orElseThrow();
    }
}
