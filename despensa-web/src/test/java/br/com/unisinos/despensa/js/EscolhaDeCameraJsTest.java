package br.com.unisinos.despensa.js;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

class EscolhaDeCameraJsTest {

    private static final Path ARQUIVO_DE_TESTES = Path.of("src", "test", "js", "escolha-camera.test.js");
    private static final Pattern RESULTADO_TAP = Pattern.compile("^(not )?ok \\d+ - (.+)$");

    @TestFactory
    List<DynamicTest> logicaDeEscolhaDaCamera() throws IOException, InterruptedException {
        Assumptions.assumeTrue(nodeDisponivel(), "Node.js não encontrado no PATH; testes de JavaScript ignorados.");
        Assumptions.assumeTrue(Files.exists(ARQUIVO_DE_TESTES), "Arquivo de testes JavaScript não encontrado.");

        Process processo = new ProcessBuilder("node", "--test", "--test-reporter=tap", ARQUIVO_DE_TESTES.toString())
                .redirectErrorStream(true)
                .start();
        String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!processo.waitFor(60, TimeUnit.SECONDS)) {
            processo.destroyForcibly();
            fail("Os testes de JavaScript demoraram demais.");
        }

        List<DynamicTest> testes = new ArrayList<>();
        String[] linhas = saida.split("\\R");
        for (int i = 0; i < linhas.length; i++) {
            Matcher resultado = RESULTADO_TAP.matcher(linhas[i]);
            if (!resultado.matches()) {
                continue;
            }
            boolean passou = resultado.group(1) == null;
            String detalhes = detalhesDaFalha(linhas, i + 1);
            testes.add(DynamicTest.dynamicTest(resultado.group(2), () -> {
                if (!passou) {
                    fail(detalhes);
                }
            }));
        }

        if (testes.isEmpty()) {
            testes.add(DynamicTest.dynamicTest("execução dos testes de JavaScript", () -> fail(saida)));
        }
        return testes;
    }

    private static String detalhesDaFalha(String[] linhas, int inicio) {
        StringBuilder detalhes = new StringBuilder();
        for (int i = inicio; i < linhas.length && linhas[i].startsWith(" "); i++) {
            detalhes.append(linhas[i]).append('\n');
        }
        return detalhes.toString();
    }

    private static boolean nodeDisponivel() {
        try {
            Process versao = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
            versao.getInputStream().readAllBytes();
            return versao.waitFor(10, TimeUnit.SECONDS) && versao.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
