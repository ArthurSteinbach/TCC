package br.com.unisinos;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Teste isolado da câmera do celular via DroidCam: lê o stream de vídeo
 * MJPEG diretamente por HTTP (sem usar a biblioteca webcam-capture, que
 * apresenta incompatibilidade de formato de cor com essa câmera virtual
 * específica — tela verde). Essa abordagem evita esse problema porque
 * decodifica os frames JPEG manualmente, um a um.
 *
 * IMPORTANTE antes de rodar:
 * - Feche o programa "DroidCam Client" do Windows (ele monopoliza a
 *   conexão e impede outro programa de acessar o stream ao mesmo tempo).
 * - Deixe só o app aberto no celular, na mesma rede Wi-Fi do computador.
 * - Ajuste a constante URL_STREAM abaixo com o IP mostrado no app do
 *   celular (pode mudar entre uma conexão e outra).
 *
 * Não faz parte do sistema final — é só uma ferramenta de verificação.
 */
public class TesteCameraCelular {

    // Ajuste este IP conforme o que aparece no app DroidCam do celular
    private static final String URL_STREAM = "http://192.168.0.105:4747/video";

    public static void main(String[] args) throws Exception {
        JLabel labelImagem = new JLabel();
        JFrame frame = new JFrame("Teste de Câmera do Celular (DroidCam)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(labelImagem);
        frame.setSize(660, 520);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        MultiFormatReader leitor = new MultiFormatReader();
        // Escopo do TCC I: só EAN-13 (código de barras do varejo brasileiro de
        // alimentos). Restringir os formatos evita leituras falsas de QR codes,
        // selos de reciclagem e outros símbolos presentes nas embalagens.
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.EAN_13));
        leitor.setHints(hints);

        System.out.println("Conectando em: " + URL_STREAM);
        HttpURLConnection conexao = (HttpURLConnection) new URL(URL_STREAM).openConnection();
        conexao.setConnectTimeout(5000);
        conexao.setReadTimeout(5000);

        try (BufferedInputStream entrada = new BufferedInputStream(conexao.getInputStream())) {
            System.out.println("Conectado! Aponte um código de barras para a câmera do celular.");
            String ultimoCodigoLido = null;

            while (true) {
                byte[] frameBytes = lerProximoFrameJpeg(entrada);
                if (frameBytes == null) {
                    System.out.println("Stream encerrado pelo celular.");
                    break;
                }

                BufferedImage imagem = ImageIO.read(new ByteArrayInputStream(frameBytes));
                if (imagem == null) {
                    continue;
                }

                SwingUtilities.invokeLater(() -> labelImagem.setIcon(new ImageIcon(imagem)));

                try {
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                            new BufferedImageLuminanceSource(imagem)));
                    // decode(bitmap) sem hints reseta os formatos possíveis a cada
                    // chamada e volta a aceitar tudo; por isso passamos hints aqui.
                    Result resultado = leitor.decode(bitmap, hints);
                    String codigo = resultado.getText();

                    if (!codigo.equals(ultimoCodigoLido)) {
                        ultimoCodigoLido = codigo;
                        System.out.println("Código detectado: " + codigo
                                + " | formato: " + resultado.getBarcodeFormat());
                    }
                } catch (NotFoundException e) {
                    // Normal: nenhum código de barras neste frame. Tenta no próximo.
                }
            }
        } catch (IOException e) {
            System.out.println("Erro ao conectar no stream: " + e.getMessage());
            System.out.println("Confirme se o DroidCam Client do Windows está FECHADO "
                    + "e se o IP em URL_STREAM está correto.");
        }
    }

    /**
     * Lê bytes de um stream MJPEG até completar um frame JPEG inteiro,
     * procurando os marcadores padrão de início (0xFFD8) e fim (0xFFD9)
     * de um arquivo JPEG. Ignora completamente os cabeçalhos de boundary
     * do multipart que ficam entre um frame e outro — não precisamos
     * entender esse formato, só procurar onde cada imagem começa e termina.
     */
    private static byte[] lerProximoFrameJpeg(InputStream entrada) throws IOException {
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
        return null; // stream fechou antes de completar um frame
    }
}
