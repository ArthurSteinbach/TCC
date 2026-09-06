package br.com.unisinos.ui;

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

    private final Consumer<String> aoLerComSucesso;
    private volatile boolean encerrando = false;
    private Thread threadLeitura;
    private FonteDeFrames fonteAtual;

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

        JButton btnConectar = new JButton("Conectar");
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

        try {
            if (radioWebcam.isSelected()) {
                Webcam webcam = (Webcam) comboWebcams.getSelectedItem();
                if (webcam == null) {
                    labelStatus.setText("Nenhuma webcam encontrada no sistema.");
                    return;
                }
                fonteAtual = new FonteWebcam(webcam);
            } else {
                fonteAtual = new FonteCelular(campoUrlCelular.getText().trim());
            }
        } catch (Exception e) {
            labelStatus.setText("Erro ao conectar: " + e.getMessage());
            return;
        }

        encerrando = false;
        labelStatus.setText("Conectado. Aponte a câmera para o código de barras...");

        threadLeitura = new Thread(this::loopDeLeitura);
        threadLeitura.setDaemon(true);
        threadLeitura.start();
    }

    private void loopDeLeitura() {
        LeitorEan13 leitor = new LeitorEan13();
        String ultimoCodigo = null;
        int repeticoes = 0;

        while (!encerrando) {
            BufferedImage imagem;
            try {
                imagem = fonteAtual.proximoFrame();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> labelStatus.setText("Erro na leitura: " + e.getMessage()));
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
        encerrando = true;
        SwingUtilities.invokeLater(() -> {
            labelStatus.setText("Código confirmado: " + codigo);
            aoLerComSucesso.accept(codigo);
            pararLeitura();
            dispose();
        });
    }

    private void pararLeitura() {
        encerrando = true;
        if (threadLeitura != null) {
            threadLeitura.interrupt();
        }
        if (fonteAtual != null) {
            fonteAtual.fechar();
            fonteAtual = null;
        }
    }
}
