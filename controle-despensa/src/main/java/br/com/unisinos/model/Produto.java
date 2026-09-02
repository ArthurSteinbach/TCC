package br.com.unisinos.model;

/**
 * Representa um produto do catálogo (ex: "Arroz Tio João 5kg").
 * Um mesmo Produto pode ter vários Lotes associados, cada um com sua
 * própria validade e preço — é essa separação que permite o FEFO.
 */
public class Produto {

    private Integer id;
    private String codigoBarras;
    private String nome;
    private String categoria;
    private String unidadeMedida;
    private String dataCadastro;

    public Produto() {
    }

    public Produto(String codigoBarras, String nome, String categoria, String unidadeMedida) {
        this.codigoBarras = codigoBarras;
        this.nome = nome;
        this.categoria = categoria;
        this.unidadeMedida = unidadeMedida;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public String getUnidadeMedida() {
        return unidadeMedida;
    }

    public void setUnidadeMedida(String unidadeMedida) {
        this.unidadeMedida = unidadeMedida;
    }

    public String getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(String dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    @Override
    public String toString() {
        return "Produto{id=" + id + ", codigoBarras='" + codigoBarras + "', nome='" + nome + "'}";
    }
}
