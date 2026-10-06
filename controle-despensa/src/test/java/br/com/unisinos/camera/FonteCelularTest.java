package br.com.unisinos.camera;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FonteCelularTest {

    private HttpServer servidor;

    @AfterEach
    void pararServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void enderecoSemProtocolo_deveInformarEnderecoInvalido() {
        ErroCameraException erro = assertThrows(ErroCameraException.class,
                () -> new FonteCelular("192.168.0.105:4747/video"));

        assertEquals(FonteCelular.MSG_ENDERECO_INVALIDO, erro.getMessage());
    }

    @Test
    void enderecoComProtocoloQueNaoEHttp_deveInformarEnderecoInvalido() {
        ErroCameraException erro = assertThrows(ErroCameraException.class,
                () -> new FonteCelular("ftp://192.168.0.105:4747/video"));

        assertEquals(FonteCelular.MSG_ENDERECO_INVALIDO, erro.getMessage());
    }

    @Test
    void portaSemNinguemEscutando_deveInformarConexaoRecusada() throws IOException {
        int portaLivre;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            portaLivre = socket.getLocalPort();
        }

        ErroCameraException erro = assertThrows(ErroCameraException.class,
                () -> new FonteCelular("http://127.0.0.1:" + portaLivre + "/video"));

        assertEquals(FonteCelular.MSG_CONEXAO_RECUSADA, erro.getMessage());
    }

    @Test
    void caminhoInexistente_deveInformarQueNaoEOVideoDaCamera() throws IOException {
        String url = iniciarServidor(troca -> {
            troca.sendResponseHeaders(404, -1);
            troca.close();
        });

        ErroCameraException erro = assertThrows(ErroCameraException.class, () -> new FonteCelular(url));

        assertEquals(FonteCelular.MSG_CAMINHO_INVALIDO, erro.getMessage());
    }

    @Test
    void streamComUmFrame_deveEntregarAImagemEDepoisInformarConexaoEncerrada() throws Exception {
        byte[] jpeg = gerarJpeg();
        String url = iniciarServidor(troca -> {
            troca.sendResponseHeaders(200, 0);
            try (OutputStream corpo = troca.getResponseBody()) {
                corpo.write("--frame\r\nContent-Type: image/jpeg\r\n\r\n".getBytes());
                corpo.write(jpeg);
                corpo.write("\r\n".getBytes());
            }
        });

        FonteCelular fonte = new FonteCelular(url);
        try {
            BufferedImage imagem = fonte.proximoFrame();
            assertNotNull(imagem);
            assertEquals(16, imagem.getWidth());

            ErroCameraException erro = assertThrows(ErroCameraException.class, fonte::proximoFrame);
            assertEquals(FonteCelular.MSG_CONEXAO_ENCERRADA, erro.getMessage());
        } finally {
            fonte.fechar();
        }
    }

    @Test
    void streamEncerradoSemNenhumFrame_deveInformarConexaoEncerrada() throws Exception {
        String url = iniciarServidor(troca -> {
            troca.sendResponseHeaders(200, 0);
            try (OutputStream corpo = troca.getResponseBody()) {
                corpo.write("<html>não é vídeo</html>".getBytes());
            }
        });

        FonteCelular fonte = new FonteCelular(url);
        try {
            ErroCameraException erro = assertThrows(ErroCameraException.class, fonte::proximoFrame);
            assertEquals(FonteCelular.MSG_CONEXAO_ENCERRADA, erro.getMessage());
        } finally {
            fonte.fechar();
        }
    }

    @Test
    void celularQueParaDeEnviarDados_deveInformarQueParouDeEnviarImagens() throws Exception {
        String url = iniciarServidor(troca -> {
            troca.sendResponseHeaders(200, 0);
            troca.getResponseBody().flush();
            try {
                Thread.sleep(8000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            troca.close();
        });

        FonteCelular fonte = new FonteCelular(url);
        try {
            ErroCameraException erro = assertThrows(ErroCameraException.class, fonte::proximoFrame);
            assertEquals(FonteCelular.MSG_PAROU_DE_ENVIAR, erro.getMessage());
        } finally {
            fonte.fechar();
        }
    }

    private String iniciarServidor(HttpHandler handler) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/video", handler);
        servidor.setExecutor(Executors.newCachedThreadPool(tarefa -> {
            Thread thread = new Thread(tarefa);
            thread.setDaemon(true);
            return thread;
        }));
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/video";
    }

    private static byte[] gerarJpeg() throws IOException {
        BufferedImage imagem = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, "jpg", saida);
        return saida.toByteArray();
    }
}
