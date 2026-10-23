package br.com.unisinos.despensa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document("usuarios")
public class Usuario {

    @Id
    private String id;

    private String nome;

    @Indexed(unique = true)
    private String email;

    private String senhaHash;

    private Boolean avisosAtivos;
    private String emailAvisos;
    private String emailAvisosPendente;

    @Indexed(unique = true, sparse = true)
    private String hashTokenConfirmacao;

    private LocalDateTime confirmacaoExpiraEm;

    @Indexed(unique = true, sparse = true)
    private String tokenDescadastro;

    private LocalDate ultimoEnvioAlertas;
    private LocalDateTime ultimoEnvioManual;

    public Usuario() {
    }

    public Usuario(String nome, String email, String senhaHash) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
    }

    public boolean recebeAvisos() {
        return avisosAtivos == null || avisosAtivos;
    }

    public String emailDeDestinoDosAvisos() {
        return emailAvisos == null || emailAvisos.isBlank() ? email : emailAvisos;
    }

    public void limparConfirmacaoPendente() {
        emailAvisosPendente = null;
        hashTokenConfirmacao = null;
        confirmacaoExpiraEm = null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public Boolean getAvisosAtivos() {
        return avisosAtivos;
    }

    public void setAvisosAtivos(Boolean avisosAtivos) {
        this.avisosAtivos = avisosAtivos;
    }

    public String getEmailAvisos() {
        return emailAvisos;
    }

    public void setEmailAvisos(String emailAvisos) {
        this.emailAvisos = emailAvisos;
    }

    public String getEmailAvisosPendente() {
        return emailAvisosPendente;
    }

    public void setEmailAvisosPendente(String emailAvisosPendente) {
        this.emailAvisosPendente = emailAvisosPendente;
    }

    public String getHashTokenConfirmacao() {
        return hashTokenConfirmacao;
    }

    public void setHashTokenConfirmacao(String hashTokenConfirmacao) {
        this.hashTokenConfirmacao = hashTokenConfirmacao;
    }

    public LocalDateTime getConfirmacaoExpiraEm() {
        return confirmacaoExpiraEm;
    }

    public void setConfirmacaoExpiraEm(LocalDateTime confirmacaoExpiraEm) {
        this.confirmacaoExpiraEm = confirmacaoExpiraEm;
    }

    public String getTokenDescadastro() {
        return tokenDescadastro;
    }

    public void setTokenDescadastro(String tokenDescadastro) {
        this.tokenDescadastro = tokenDescadastro;
    }

    public LocalDate getUltimoEnvioAlertas() {
        return ultimoEnvioAlertas;
    }

    public void setUltimoEnvioAlertas(LocalDate ultimoEnvioAlertas) {
        this.ultimoEnvioAlertas = ultimoEnvioAlertas;
    }

    public LocalDateTime getUltimoEnvioManual() {
        return ultimoEnvioManual;
    }

    public void setUltimoEnvioManual(LocalDateTime ultimoEnvioManual) {
        this.ultimoEnvioManual = ultimoEnvioManual;
    }
}
