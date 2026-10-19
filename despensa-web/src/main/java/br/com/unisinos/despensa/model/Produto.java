package br.com.unisinos.despensa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Document("produtos")
@CompoundIndex(name = "usuario_codigo_barras", def = "{'usuarioId': 1, 'codigoBarras': 1}", unique = true)
public class Produto {

    @Id
    private String id;

    @Version
    private Long versao;

    private String usuarioId;
    private String codigoBarras;
    private String nome;
    private String categoria;
    private LocalDateTime dataCadastro;
    private List<Lote> lotes = new ArrayList<>();

    public Produto() {
    }

    public Produto(String usuarioId, String codigoBarras, String nome, String categoria,
                   LocalDateTime dataCadastro) {
        this.usuarioId = usuarioId;
        this.codigoBarras = codigoBarras;
        this.nome = nome;
        this.categoria = categoria;
        this.dataCadastro = dataCadastro;
    }

    public Optional<Lote> buscarLote(String loteId) {
        return lotes.stream().filter(l -> l.getId().equals(loteId)).findFirst();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersao() {
        return versao;
    }

    public void setVersao(Long versao) {
        this.versao = versao;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public void setCodigoBarras(String codigoBarras) {
        this.codigoBarras = codigoBarras;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDateTime dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    public List<Lote> getLotes() {
        return lotes;
    }

    public void setLotes(List<Lote> lotes) {
        this.lotes = lotes;
    }
}
