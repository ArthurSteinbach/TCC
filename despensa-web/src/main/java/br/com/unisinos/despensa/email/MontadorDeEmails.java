package br.com.unisinos.despensa.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class MontadorDeEmails {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_E_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final String urlPublica;

    public MontadorDeEmails(@Value("${despensa.url-publica}") String urlPublica) {
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    public MensagemEmail alertas(String nome, String destinatario, List<ItemAviso> vencidos, List<ItemAviso> vencendo,
                                 String tokenDescadastro, LocalDate dia) {
        String assunto = "Despensa: " + resumo(vencidos.size(), vencendo.size()) + " (" + dia.format(DIA_E_MES) + ")";
        StringBuilder html = new StringBuilder(cabecalhoHtml(nome));
        StringBuilder texto = new StringBuilder("Olá, " + nome + "!\n\n");

        html.append("<p>Estes itens da sua despensa precisam de atenção:</p>");
        texto.append("Estes itens da sua despensa precisam de atenção:\n");
        secao(html, texto, "Vencidos", vencidos, "#b42318");
        secao(html, texto, "Vencendo em breve", vencendo, "#8a6d00");

        String alertas = urlPublica + "/alertas";
        html.append("<p style=\"margin:24px 0\"><a href=\"").append(escapar(alertas))
                .append("\" style=\"background:#198754;color:#fff;padding:12px 18px;border-radius:6px;")
                .append("text-decoration:none;display:inline-block\">Ver os alertas na despensa</a></p>");
        texto.append("\nVer os alertas na despensa: ").append(alertas).append('\n');

        rodape(html, texto, tokenDescadastro);
        return new MensagemEmail(destinatario, assunto, html.toString(), texto.toString());
    }

    public MensagemEmail teste(String nome, String destinatario, String tokenDescadastro) {
        StringBuilder html = new StringBuilder(cabecalhoHtml(nome));
        StringBuilder texto = new StringBuilder("Olá, " + nome + "!\n\n");
        html.append("<p>Este é um e-mail de teste do Controle de Despensa. Se você recebeu esta mensagem, ")
                .append("os avisos de vencimento vão chegar neste endereço.</p>");
        texto.append("Este é um e-mail de teste do Controle de Despensa. Se você recebeu esta mensagem, ")
                .append("os avisos de vencimento vão chegar neste endereço.\n");
        rodape(html, texto, tokenDescadastro);
        return new MensagemEmail(destinatario, "Despensa: e-mail de teste", html.toString(), texto.toString());
    }

    public MensagemEmail confirmacao(String nome, String novoEmail, String token, LocalDateTime expiraEm) {
        String link = UriComponentsBuilder.fromUriString(urlPublica + "/notificacoes/confirmar")
                .queryParam("token", token).build().toUriString();
        String prazo = expiraEm.format(DATA_HORA);
        String html = cabecalhoHtml(nome)
                + "<p>Recebemos um pedido para enviar os avisos de vencimento do Controle de Despensa para "
                + "este e-mail.</p><p style=\"margin:24px 0\"><a href=\"" + escapar(link)
                + "\" style=\"background:#198754;color:#fff;padding:12px 18px;border-radius:6px;"
                + "text-decoration:none;display:inline-block\">Confirmar este e-mail</a></p>"
                + "<p style=\"color:#555\">O link vale até " + escapar(prazo) + ". Se você não pediu, ignore esta "
                + "mensagem: nada muda.</p></div>";
        String texto = "Olá, " + nome + "!\n\nRecebemos um pedido para enviar os avisos de vencimento do Controle "
                + "de Despensa para este e-mail.\n\nConfirme abrindo o link:\n" + link + "\n\nO link vale até "
                + prazo + ". Se você não pediu, ignore esta mensagem: nada muda.\n";
        return new MensagemEmail(novoEmail, "Despensa: confirme o e-mail para os avisos", html, texto);
    }

    String linkDescadastro(String tokenDescadastro) {
        return UriComponentsBuilder.fromUriString(urlPublica + "/notificacoes/descadastrar")
                .queryParam("token", tokenDescadastro).build().toUriString();
    }

    private void secao(StringBuilder html, StringBuilder texto, String titulo, List<ItemAviso> itens, String cor) {
        if (itens.isEmpty()) {
            return;
        }
        html.append("<h2 style=\"font-size:18px;color:").append(cor).append(";margin:20px 0 8px\">")
                .append(escapar(titulo)).append("</h2>")
                .append("<table style=\"border-collapse:collapse;width:100%;font-size:14px\">")
                .append("<tr style=\"text-align:left;border-bottom:1px solid #ddd\"><th style=\"padding:6px\">Produto</th>")
                .append("<th style=\"padding:6px\">Quantidade</th><th style=\"padding:6px\">Validade</th>")
                .append("<th style=\"padding:6px\">Prazo</th></tr>");
        texto.append('\n').append(titulo.toUpperCase()).append('\n');
        for (ItemAviso item : itens) {
            String validade = item.dataValidade().format(DATA);
            html.append("<tr style=\"border-bottom:1px solid #eee\"><td style=\"padding:6px\">")
                    .append(escapar(item.nomeProduto())).append("</td><td style=\"padding:6px\">")
                    .append(item.quantidadeFormatada()).append("</td><td style=\"padding:6px\">")
                    .append(validade).append("</td><td style=\"padding:6px\">")
                    .append(escapar(item.prazo())).append("</td></tr>");
            texto.append("- ").append(item.nomeProduto()).append(": ").append(item.quantidadeFormatada())
                    .append(" un., validade ").append(validade).append(" (").append(item.prazo()).append(")\n");
        }
        html.append("</table>");
    }

    private void rodape(StringBuilder html, StringBuilder texto, String tokenDescadastro) {
        String descadastro = linkDescadastro(tokenDescadastro);
        html.append("<hr style=\"border:none;border-top:1px solid #ddd;margin:24px 0\">")
                .append("<p style=\"font-size:12px;color:#777\">Você recebe este e-mail porque ligou os avisos de ")
                .append("vencimento no Controle de Despensa. <a href=\"").append(escapar(descadastro))
                .append("\" style=\"color:#777\">Não quero mais receber estes avisos</a>.</p></div>");
        texto.append("\n--\nVocê recebe este e-mail porque ligou os avisos de vencimento no Controle de Despensa.\n")
                .append("Para não receber mais: ").append(descadastro).append('\n');
    }

    private static String cabecalhoHtml(String nome) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#1f2933;max-width:600px\">"
                + "<h1 style=\"font-size:20px;color:#198754\">Controle de Despensa</h1>"
                + "<p>Olá, " + escapar(nome) + "!</p>";
    }

    private static String resumo(int vencidos, int vencendo) {
        String parteVencidos = vencidos == 1 ? "1 item vencido" : vencidos + " itens vencidos";
        String parteVencendo = vencendo == 1 ? "1 item vencendo em breve" : vencendo + " itens vencendo em breve";
        if (vencidos > 0 && vencendo > 0) {
            return parteVencidos + " e " + parteVencendo;
        }
        return vencidos > 0 ? parteVencidos : parteVencendo;
    }

    private static String escapar(String valor) {
        return HtmlUtils.htmlEscape(valor == null ? "" : valor, "UTF-8");
    }
}
