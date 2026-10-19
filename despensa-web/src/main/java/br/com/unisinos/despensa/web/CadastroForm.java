package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.exception.ValidacaoException;
import br.com.unisinos.despensa.service.LoteService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
        int valor;
        try {
            valor = Integer.parseInt(quantidade == null ? "" : quantidade.trim());
        } catch (NumberFormatException e) {
            valor = 0;
        }
        if (valor <= 0) {
            throw new ValidacaoException("Informe a quantidade (um número inteiro maior que zero).");
        }
        return valor;
    }

    public double precoInformado() {
        try {
            return new BigDecimal(precoUnitario == null ? "" : precoUnitario.trim().replace(',', '.')).doubleValue();
        } catch (NumberFormatException e) {
            throw new ValidacaoException("Informe o preço unitário (ex: 4,99).");
        }
    }

    public Map<LocalDate, Integer> quantidadePorValidade() {
        int unidades = quantidadeInformada();
        if (mesmaValidade) {
            Map<LocalDate, Integer> unica = new LinkedHashMap<>();
            unica.put(lerData(validade, "Informe a data de validade."), unidades);
            return unica;
        }

        if (unidades > LIMITE_UNIDADES_COM_VALIDADES_DIFERENTES) {
            throw new ValidacaoException("Para validades diferentes, cadastre no máximo "
                    + LIMITE_UNIDADES_COM_VALIDADES_DIFERENTES + " unidades por vez.");
        }
        List<LocalDate> datas = new ArrayList<>();
        for (int i = 0; i < unidades; i++) {
            String texto = i < validades.size() ? validades.get(i) : null;
            datas.add(lerData(texto, "Informe a validade de todas as unidades (a unidade " + (i + 1)
                    + " está sem data)."));
        }
        return LoteService.agruparPorValidade(datas);
    }

    private static LocalDate lerData(String texto, String mensagemSeVazio) {
        if (texto == null || texto.isBlank()) {
            throw new ValidacaoException(mensagemSeVazio);
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ValidacaoException("Data de validade inválida.");
        }
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
