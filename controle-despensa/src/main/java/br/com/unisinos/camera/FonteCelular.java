package br.com.unisinos.camera;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Fonte de frames vinda de um stream MJPEG via rede (ex: celular rodando
 * DroidCam). Lê os frames JPEG diretamente pelos bytes, procurando os
 * marcadores de início/fim de cada imagem, sem depender de bibliotecas
 * externas de captura de vídeo.
 */
public class FonteCelular implements FonteDeFrames {

    private final BufferedInputStream entrada;

    public FonteCelular(String urlStream) throws IOException {
        HttpURLConnection conexao = (HttpURLConnection) new URL(urlStream).openConnection();
        conexao.setConnectTimeout(5000);
        conexao.setReadTimeout(5000);
        this.entrada = new BufferedInputStream(conexao.getInputStream());
    }

    @Override
    public BufferedImage proximoFrame() throws IOException {
        byte[] frameBytes = lerProximoFrameJpeg(entrada);
        if (frameBytes == null) {
            return null;
        }
        return ImageIO.read(new ByteArrayInputStream(frameBytes));
    }

    @Override
    public void fechar() {
        try {
            entrada.close();
        } catch (IOException e) {
            // Conexão já pode estar fechada pelo outro lado; sem problema.
        }
    }

    /**
     * Lê bytes do stream até completar um frame JPEG inteiro, procurando
     * os marcadores padrão de início (0xFFD8) e fim (0xFFD9). Ignora os
     * cabeçalhos de boundary do multipart entre um frame e outro.
     */
    private byte[] lerProximoFrameJpeg(InputStream entrada) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int anterior = -1;
        int atual;
        boolean dentroDoFrame = false;

        while ((atual = entrada.read()) != -1) {
            if (!dentroDoFrame) {
                if (anterior == 0xFF && atual == 0xD8) {
                    dentroDoFrame = true;
                    buffer.write(0xFF);
                    buffer.write(0xD8);
                }
            } else {
                buffer.write(atual);
                if (anterior == 0xFF && atual == 0xD9) {
                    return buffer.toByteArray();
                }
            }
            anterior = atual;
        }
        return null;
    }
}
