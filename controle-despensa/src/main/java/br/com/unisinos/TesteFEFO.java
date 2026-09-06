package br.com.unisinos;

import br.com.unisinos.db.DatabaseManager;
import br.com.unisinos.exception.ValidacaoException;
import br.com.unisinos.model.Lote;
import br.com.unisinos.model.Produto;
import br.com.unisinos.service.LoteService;
import br.com.unisinos.service.ProdutoService;
import br.com.unisinos.util.Constantes;

import java.time.LocalDate;
import java.util.List;

/**
 * Teste manual da camada de Service: confirma tanto o "caminho feliz"
 * (FEFO, alerta, perda financeira) quanto as validações de negócio
 * (ValidacaoException disparando nos casos que devem ser recusados).
 *
 * Este arquivo é descartável, não faz parte do sistema final.
 */
public class TesteFEFO {

    public static void main(String[] args) throws Exception {
        DatabaseManager.inicializar();

        ProdutoService produtoService = new ProdutoService();
        LoteService loteService = new LoteService();

        System.out.println("========== PARTE 1: caminho feliz (dados válidos) ==========\n");

        String codigoTeste = "TESTE-" + System.currentTimeMillis();
        Produto arroz = produtoService.cadastrar(codigoTeste, "Arroz Tio João 5kg", "Grãos");
        System.out.println("Produto cadastrado: " + arroz);

        Lote loteA = loteService.cadastrar(arroz.getId(), 2, 25.90, LocalDate.now().plusDays(30));
        Lote loteB = loteService.cadastrar(arroz.getId(), 1, 24.50, LocalDate.now().plusDays(3));
        Lote loteC = loteService.cadastrar(arroz.getId(), 3, 26.00, LocalDate.now().plusDays(10));
        System.out.println("3 lotes cadastrados com validades em 30, 3 e 10 dias.");

        System.out.println("\n--- Lotes ativos ordenados por FEFO ---");
        List<Lote> ativos = loteService.listarAtivosFEFO();
        for (Lote l : ativos) {
            if (l.getProdutoId().equals(arroz.getId())) {
                System.out.println(l.getNomeProduto() + " | validade: " + l.getDataValidade());
            }
        }

        System.out.println("\n--- Lotes vencendo em até " + Constantes.DIAS_ALERTA_VENCIMENTO + " dias ---");
        List<Lote> vencendo = loteService.listarAlertaVencimento();
        for (Lote l : vencendo) {
            if (l.getProdutoId().equals(arroz.getId())) {
                System.out.println(l.getNomeProduto() + " | validade: " + l.getDataValidade());
            }
        }

        loteService.descartar(loteA.getId());
        double perdaTotal = loteService.calcularPerdaFinanceiraTotal();
        System.out.println("\nLote A marcado como DESCARTADO.");
        System.out.println("Perda financeira acumulada no sistema: R$ " + perdaTotal);

        System.out.println("\n\n========== PARTE 2: validações (devem ser recusadas) ==========\n");

        // Caso 1: tentar cadastrar um lote com validade no passado
        try {
            loteService.cadastrar(arroz.getId(), 1, 10.0, LocalDate.now().minusDays(1));
            System.out.println("[FALHOU] O sistema deveria ter recusado um lote já vencido, mas aceitou.");
        } catch (ValidacaoException e) {
            System.out.println("[OK] Lote vencido recusado corretamente. Mensagem: \"" + e.getMessage() + "\"");
        }

        // Caso 2: tentar cadastrar um produto com código de barras já existente
        try {
            produtoService.cadastrar(codigoTeste, "Outro Nome Qualquer", "Outra Categoria");
            System.out.println("[FALHOU] O sistema deveria ter recusado um código de barras duplicado, mas aceitou.");
        } catch (ValidacaoException e) {
            System.out.println("[OK] Código de barras duplicado recusado corretamente. Mensagem: \"" + e.getMessage() + "\"");
        }

        // Caso 3: tentar cadastrar um lote com quantidade inválida
        try {
            loteService.cadastrar(arroz.getId(), 0, 10.0, LocalDate.now().plusDays(5));
            System.out.println("[FALHOU] O sistema deveria ter recusado quantidade zero, mas aceitou.");
        } catch (ValidacaoException e) {
            System.out.println("[OK] Quantidade inválida recusada corretamente. Mensagem: \"" + e.getMessage() + "\"");
        }

        System.out.println("\nTeste concluído. Todas as linhas marcadas [OK] confirmam que a camada de Service está protegendo o sistema.");
    }
}
