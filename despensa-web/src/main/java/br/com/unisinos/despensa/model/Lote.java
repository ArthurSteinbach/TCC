package br.com.unisinos.despensa.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Lote {

    private String id;
    private double quantidade;
    private double precoUnitario;
    private LocalDate dataValidade;
    private LocalDateTime dataCadastro;
    private StatusLote status;
    private LocalDateTime dataBaixa;
    private LocalDateTime avisoVencendoEm;
    private LocalDateTime avisoVencidoEm;

    public Lote() {
    }

    public Lote(String id, double quantidade, double precoUnitario, LocalDate dataValidade,
                LocalDateTime dataCadastro) {
        this.id = id;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
        this.dataValidade = dataValidade;
        this.dataCadastro = dataCadastro;
        this.status = StatusLote.ATIVO;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDateTime dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    public StatusLote getStatus() {
        return status;
    }

    public void setStatus(StatusLote status) {
        this.status = status;
    }

    public LocalDateTime getDataBaixa() {
        return dataBaixa;
    }

    public void setDataBaixa(LocalDateTime dataBaixa) {
        this.dataBaixa = dataBaixa;
    }

    public LocalDateTime getAvisoVencendoEm() {
        return avisoVencendoEm;
    }

    public void setAvisoVencendoEm(LocalDateTime avisoVencendoEm) {
        this.avisoVencendoEm = avisoVencendoEm;
    }

    public LocalDateTime getAvisoVencidoEm() {
        return avisoVencidoEm;
    }

    public void setAvisoVencidoEm(LocalDateTime avisoVencidoEm) {
        this.avisoVencidoEm = avisoVencidoEm;
    }

    public void zerarAvisos() {
        avisoVencendoEm = null;
        avisoVencidoEm = null;
    }

    public boolean isAtivo() {
        return status == StatusLote.ATIVO;
    }

    public boolean isVencidoEm(LocalDate data) {
        return dataValidade.isBefore(data);
    }

    public double getValorTotal() {
        return quantidade * precoUnitario;
    }
}
