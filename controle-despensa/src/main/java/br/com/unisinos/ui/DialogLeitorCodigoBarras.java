package br.com.unisinos.ui;

import br.com.unisinos.camera.ErroCameraException;
import br.com.unisinos.camera.FonteCelular;
import br.com.unisinos.camera.FonteDeFrames;
import br.com.unisinos.camera.FonteWebcam;
import br.com.unisinos.camera.LeitorEan13;
import com.github.sarxos.webcam.Webcam;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Diálogo de leitura de código de barras via câmera. Suporta duas fontes,
 * conforme o TCC I (item 1.2): uma webcam física conectada ao computador,
 * ou a câmera do celular via stream de rede (ex: DroidCam). Um código só
 * é aceito depois de ser lido repetidas vezes seguidas de forma idêntica,
 * evitando falsos positivos causados por frames borrados em movimento.
 */
public class DialogLeitorCodigoBarras extends JDialog {

    private static final int LEITURAS_PARA_CONFIRMAR = 2;

    private final JLabel labelPreview = new JLabel();
    private final JLabel labelStatus = new JLabel("Escolha a fonte e clique em Conectar.");
    private final JComboBox<Webcam> comboWebcams = new JComboBox<>();
    private final JTextField campoUrlCelular = new JTextField("http://192.168.0.105:4747/video", 25);
    private final JRadioButton radioWebcam = new JRadioButton("Webcam do computador", true);
    private final JRadioButton radioCelular = new JRadioButton("Celular (rede — ex: DroidCam)");
    private final JButton btnConectar = new JButton("Conectar");

    private final Consumer<String> aoLerComSucesso;
    private Thread threadLeitura;
    private volatile FonteDeFrames fonteAtual;

    public DialogLeitorCodigoBarras(Window parent, Consumer<String> aoLerComSucesso) {
        super(parent, "Ler Código de Barras", ModalityType.APPLICATION_MODAL);
        this.aoLerComSucesso = aoLerComSucesso;
        setSize(700, 620);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        montarLayout();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                pararLeitura();
            }
        });
    }

    private void montarLayout() {
        ButtonGroup grupo = new ButtonGroup();
        grupo.add(radioWebcam);
        grupo.add(radioCelular);

        for (Webcam w : Webcam.getWebcams()) {
            comboWebcams.addItem(w);
        }

        JPanel painelFonte = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        painelFonte.add(radioWebcam, c);
        c.gridx = 1;
        painelFonte.add(comboWebcams, c);
        c.gridx = 0;
        c.gridy = 1;
        painelFonte.add(radioCelular, c);
        c.gridx = 1;
        painelFonte.add(campoUrlCelular, c);

        btnConectar.addActionListener(e -> iniciarLeitura());

        JPanel painelBotoesTopo = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        painelBotoesTopo.add(btnConectar);

        JPanel painelTopo = new JPanel(new BorderLayout());
        painelTopo.add(painelFonte, BorderLayout.CENTER);
        painelTopo.add(painelBotoesTopo, BorderLayout.SOUTH);
        add(painelTopo, BorderLayout.NORTH);

        labelPreview.setHorizontalAlignment(SwingConstants.CENTER);
        labelPreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        add(labelPreview, BorderLayout.CENTER);

        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.addActionListener(e -> {
            pararLeitura();
            dispose();
        });

        labelStatus.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel painelBotoesInferior = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        painelBotoesInferior.add(btnCancelar);

        JPanel painelInferior = new JPanel(new BorderLayout());
        painelInferior.add(labelStatus, BorderLayout.CENTER);
        painelInferior.add(painelBotoesInferior, BorderLayout.SOUTH);
        add(painelInferior, BorderLayout.SOUTH);
    }

    private void iniciarLeitura() {
        pararLeitura(); // encerra qualquer leitura anterior antes de começar outra

        boolean usarWebcam = radioWebcam.isSelected();
        Webcam webcam = (Webcam) comboWebcams.getSelectedItem();
        String urlCelular = campoUrlCelular.getText().trim();

        if (usarWebcam && webcam == null) {
            labelStatus.setText("Nenhuma webcam encontrada no sistema.");
            return;
        }

        labelStatus.setText("Conectando...");
        btnConectar.setEnabled(false);

        new SwingWorker<FonteDeFrames, Void>() {
            @Override
            protected FonteDeFrames doInBackground() throws Exception {
                return usarWebcam ? new FonteWebcam(webcam) : new FonteCelular(urlCelular);
            }

            @Override
            protected void done() {
                btnConectar.setEnabled(true);

                FonteDeFrames fonte;
                try {
                    fonte = get();
                } catch (ExecutionException e) {
                    labelStatus.setText(mensagemDeErro(e.getCause(), usarWebcam));
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (!isVisible()) {
                    fonte.fechar();
                    return;
                }

                fonteAtual = fonte;
                labelStatus.setText("Conectado. Aponte a câmera para o código de barras...");

                threadLeitura = new Thread(() -> loopDeLeitura(fonte));
                threadLeitura.setDaemon(true);
                threadLeitura.start();
            }
        }.execute();
    }

    private static String mensagemDeErro(Throwable erro, boolean usarWebcam) {
        if (erro instanceof ErroCameraException) {
            return erro.getMessage();
        }
        if (usarWebcam) {
            return "Não foi possível usar a webcam. Confira se ela está conectada e se não está aberta em outro programa.";
        }
        return "Não foi possível ler a câmera do celular. Confira o endereço e tente conectar de novo.";
    }

    private void loopDeLeitura(FonteDeFrames fonte) {
        LeitorEan13 leitor = new LeitorEan13();
        String ultimoCodigo = null;
        int repeticoes = 0;
        boolean usarWebcam = fonte instanceof FonteWebcam;

        while (fonteAtual == fonte) {
            BufferedImage imagem;
            try {
                imagem = fonte.proximoFrame();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (fonteAtual == fonte) {
                        labelStatus.setText(mensagemDeErro(e, usarWebcam));
                    }
                });
                break;
            }

            if (imagem == null) {
                continue;
            }

            BufferedImage imagemParaExibir = imagem;
            SwingUtilities.invokeLater(() -> labelPreview.setIcon(new ImageIcon(imagemParaExibir)));

            Optional<String> resultado = leitor.tentarLer(imagem);
            if (resultado.isPresent()) {
                String codigo = resultado.get();
                if (codigo.equals(ultimoCodigo)) {
                    repeticoes++;
                } else {
                    ultimoCodigo = codigo;
                    repeticoes = 1;
                }

                final int repeticoesAtuais = repeticoes;
                final String codigoAtual = codigo;
                SwingUtilities.invokeLater(() -> labelStatus.setText(
                        "Lendo " + codigoAtual + " (" + repeticoesAtuais + "/" + LEITURAS_PARA_CONFIRMAR + ")..."));

                if (repeticoes >= LEITURAS_PARA_CONFIRMAR) {
                    confirmarLeitura(codigo);
                    break;
                }
            } else {
                ultimoCodigo = null;
                repeticoes = 0;
            }

            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void confirmarLeitura(String codigo) {
        SwingUtilities.invokeLater(() -> {
            labelStatus.setText("Código confirmado: " + codigo);
            aoLerComSucesso.accept(codigo);
            pararLeitura();
            dispose();
        });
    }

    private void pararLeitura() {
        FonteDeFrames fonte = fonteAtual;
        fonteAtual = null;
        if (threadLeitura != null) {
            threadLeitura.interrupt();
        }
        if (fonte != null) {
            fonte.fechar();
        }
    }
}
