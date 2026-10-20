package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.service.LoteService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CadastroForm {

    public static final int LIMITE_UNIDADES_COM_VALIDADES_DIFERENTES = 50;

    private String codigoBarras = "";
    private String nome = "";
    private String categoria = "";
    private String quantidade = "1";
    private String precoUnitario = "";
    private boolean mesmaValidade = true;
    private String validade = "";
    private List<String> validades = new ArrayList<>();

    public int quantidadeInformada() {
        return LeituraDeCampos.quantidadeInteira(quantidade);
    }

    public double precoInformado() {
        return LeituraDeCampos.preco(precoUnitario);
    }

    public Map<LocalDate, Integer> quantidadePorValidade() {
        int unidades = quantidadeInformada();
        if (mesmaValidade) {
            Map<LocalDate, Integer> unica = new LinkedHashMap<>();
            unica.put(LeituraDeCampos.data(validade, "Informe a data de validade."), unidades);
            return unica;
        }

        if (unidades > LIMITE_UNIDADES_COM_VALIDADES_DIFERENTES) {
            throw new ValidacaoException("Para validades diferentes, cadastre no máximo "
                    + LIMITE_UNIDADES_COM_VALIDADES_DIFERENTES + " unidades por vez.");
        }
        List<LocalDate> datas = new ArrayList<>();
        for (int i = 0; i < unidades; i++) {
            String texto = i < validades.size() ? validades.get(i) : null;
            datas.add(LeituraDeCampos.data(texto, "Informe a validade de todas as unidades (a unidade " + (i + 1)
                    + " está sem data)."));
        }
        return LoteService.agruparPorValidade(datas);
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public void setCodigoBarras(String codigoBarras) {
        this.codigoBarras = codigoBarras == null ? "" : codigoBarras.trim();
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

    public String getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(String quantidade) {
        this.quantidade = quantidade;
    }

    public String getPrecoUnitario() {
        return precoUnitario;
    }

    public void setPrecoUnitario(String precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    public boolean isMesmaValidade() {
        return mesmaValidade;
    }

    public void setMesmaValidade(boolean mesmaValidade) {
        this.mesmaValidade = mesmaValidade;
    }

    public String getValidade() {
        return validade;
    }

    public void setValidade(String validade) {
        this.validade = validade;
    }

    public List<String> getValidades() {
        return validades;
    }

    public void setValidades(List<String> validades) {
        this.validades = validades == null ? new ArrayList<>() : validades;
    }
}
