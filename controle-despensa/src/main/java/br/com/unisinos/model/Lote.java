package br.com.unisinos.model;

import java.time.LocalDate;

/**
 * Representa um lote físico de um produto: uma compra específica, com sua
 * própria quantidade, preço e data de validade. É no nível do Lote que a
 * regra FEFO (First to Expire, First Out) é aplicada.
 */
public class Lote {

    private Integer id;
    private Integer produtoId;
    private double quantidade;
    private double precoUnitario;
    private LocalDate dataValidade;
    private String dataCadastro;
    private StatusLote status;
    private String dataBaixa;

    // Campos auxiliares, não persistidos diretamente nesta tabela: são
    // preenchidos via JOIN com produtos nas consultas de listagem (FEFO,
    // alertas), para já entregar o nome e código de barras prontos pra tela.
    private String nomeProduto;
    private String codigoBarrasProduto;

    public Lote() {
    }

    public Lote(Integer produtoId, double quantidade, double precoUnitario, LocalDate dataValidade) {
        this.produtoId = produtoId;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
        this.dataValidade = dataValidade;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(Integer produtoId) {
        this.produtoId = produtoId;
    }

    public double getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(double quantidade) {
        this.quantidade = quantidade;
    }

    public double getPrecoUnitario() {
        return precoUnitario;
    }

    public void setPrecoUnitario(double precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    public LocalDate getDataValidade() {
        return dataValidade;
    }

    public void setDataValidade(LocalDate dataValidade) {
        this.dataValidade = dataValidade;
    }

    public String getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(String dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    public StatusLote getStatus() {
        return status;
    }

    public void setStatus(StatusLote status) {
        this.status = status;
    }

    public String getDataBaixa() {
        return dataBaixa;
    }

    public void setDataBaixa(String dataBaixa) {
        this.dataBaixa = dataBaixa;
    }

    public String getNomeProduto() {
        return nomeProduto;
    }

    public void setNomeProduto(String nomeProduto) {
        this.nomeProduto = nomeProduto;
    }

    public String getCodigoBarrasProduto() {
        return codigoBarrasProduto;
    }

    public void setCodigoBarrasProduto(String codigoBarrasProduto) {
        this.codigoBarrasProduto = codigoBarrasProduto;
    }

    /** Valor financeiro deste lote (quantidade x preço unitário). */
    public double getValorTotal() {
        return quantidade * precoUnitario;
    }

    @Override
    public String toString() {
        return "Lote{id=" + id + ", produtoId=" + produtoId + ", validade=" + dataValidade
                + ", status=" + status + "}";
    }
}
