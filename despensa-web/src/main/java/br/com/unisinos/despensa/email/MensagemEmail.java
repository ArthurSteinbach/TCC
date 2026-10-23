package br.com.unisinos.despensa.email;

public record MensagemEmail(String destinatario, String assunto, String html, String texto) {
}
