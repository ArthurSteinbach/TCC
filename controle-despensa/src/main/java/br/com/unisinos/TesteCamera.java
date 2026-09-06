package br.com.unisinos;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import javax.swing.JFrame;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Teste isolado de hardware: abre a webcam padrão do sistema e tenta
 * decodificar, em tempo real, qualquer código de barras apontado para ela.
 *
 * Não faz parte do sistema final — é só uma ferramenta de verificação,
 * pra confirmar que webcam-capture + ZXing funcionam neste computador
 * antes de integrar essa leitura nas telas de cadastro.
 *
 * Como usar: rode esta classe, aponte um código de barras real (uma
 * embalagem, ou a tela do celular com um código de barras aberto) para
 * a câmera, e observe o console. Feche a janela para encerrar.
 */
public class TesteCamera {

    public static void main(String[] args) {
        List<Webcam> cameras = Webcam.getWebcams();

        if (cameras.isEmpty()) {
            System.out.println("Nenhuma webcam foi encontrada pelo sistema. "
                    + "Verifique se há uma câmera conectada e se o Windows tem permissão de acesso a ela.");
            return;
        }

        System.out.println("Câmeras disponíveis:");
        for (int i = 0; i < cameras.size(); i++) {
            System.out.println(i + ": " + cameras.get(i).getName());
        }

        final Webcam webcam = cameras.size() == 1 ? cameras.get(0) : escolherCamera(cameras);

        System.out.println("Webcam escolhida: " + webcam.getName());

        webcam.setViewSize(new Dimension(640, 480));
        webcam.open();

        WebcamPanel painel = new WebcamPanel(webcam);
        painel.setFPSDisplayed(true);
        painel.setPreferredSize(new Dimension(640, 480));

        JFrame frame = new JFrame("Teste de Câmera — aponte um código de barras");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(painel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        MultiFormatReader leitor = new MultiFormatReader();
        // Escopo do TCC I: só EAN-13 (código de barras do varejo brasileiro de
        // alimentos). Restringir os formatos evita leituras falsas de QR codes,
        // selos de reciclagem e outros símbolos presentes nas embalagens.
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.EAN_13));
        leitor.setHints(hints);

        Thread threadLeitura = new Thread(() -> {
            String ultimoCodigoLido = null;

            while (webcam.isOpen()) {
                BufferedImage imagem = webcam.getImage();

                if (imagem != null) {
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
                        // Normal: nenhum código de barras no frame atual. Tenta de novo no próximo.
                    }
                }

                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        threadLeitura.setDaemon(true);
        threadLeitura.start();

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                webcam.close();
                System.out.println("Câmera fechada. Teste encerrado.");
            }
        });
    }

    /** Pergunta no console qual câmera usar quando há mais de uma disponível. */
    private static Webcam escolherCamera(List<Webcam> cameras) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Digite o número da câmera que quer usar: ");
            String entrada = scanner.nextLine().trim();
            try {
                int escolhido = Integer.parseInt(entrada);
                if (escolhido >= 0 && escolhido < cameras.size()) {
                    return cameras.get(escolhido);
                }
                System.out.println("Índice fora do intervalo. Escolha entre 0 e " + (cameras.size() - 1) + ".");
            } catch (NumberFormatException e) {
                System.out.println("Entrada inválida. Digite apenas o número da câmera.");
            }
        }
    }
}
