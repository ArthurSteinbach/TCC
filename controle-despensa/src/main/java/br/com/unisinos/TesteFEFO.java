package br.com.unisinos;

import br.com.unisinos.dao.LoteDAO;
import br.com.unisinos.dao.ProdutoDAO;
import br.com.unisinos.db.DatabaseManager;
import br.com.unisinos.model.Lote;
import br.com.unisinos.model.Produto;
import br.com.unisinos.util.Constantes;

import java.time.LocalDate;
import java.util.List;

/**
 * Teste manual da camada de dados: cadastra um produto fictício com
 * múltiplos lotes de validades diferentes e confirma que o FEFO
 * (listarAtivosFEFO) devolve na ordem certa: validade mais próxima primeiro.
 *
 * Depois de confirmar que funciona, este arquivo pode ser apagado — ele
 * não faz parte do sistema final, é só uma ferramenta de verificação.
 */
public class TesteFEFO {

    public static void main(String[] args) throws Exception {
        DatabaseManager.inicializar();

        ProdutoDAO produtoDAO = new ProdutoDAO();
        LoteDAO loteDAO = new LoteDAO();

        // 1. Cadastra um produto de teste (usa um código de barras "falso"
        //    só pra não colidir se você rodar este teste mais de uma vez)
        String codigoTeste = "TESTE-" + System.currentTimeMillis();
        Produto arroz = new Produto(codigoTeste, "Arroz Tio João 5kg", "Grãos", "UN");
        produtoDAO.inserir(arroz);
        System.out.println("Produto cadastrado: " + arroz);

        // 2. Cadastra 3 lotes do mesmo produto, com validades fora de ordem
        //    de propósito, pra provar que o FEFO reordena de verdade
        Lote loteA = new Lote(arroz.getId(), 2, 25.90, LocalDate.now().plusDays(30));
        Lote loteB = new Lote(arroz.getId(), 1, 24.50, LocalDate.now().plusDays(3));  // vence primeiro
        Lote loteC = new Lote(arroz.getId(), 3, 26.00, LocalDate.now().plusDays(10));

        loteDAO.inserir(loteA);
        loteDAO.inserir(loteB);
        loteDAO.inserir(loteC);
        System.out.println("3 lotes cadastrados com validades em 30, 3 e 10 dias.");

        // 3. Consulta FEFO: espera-se a ordem loteB (3 dias), loteC (10 dias), loteA (30 dias)
        System.out.println("\n--- Lotes ativos ordenados por FEFO ---");
        List<Lote> ativos = loteDAO.listarAtivosFEFO();
        for (Lote l : ativos) {
            if (l.getProdutoId().equals(arroz.getId())) {
                System.out.println(l.getNomeProduto() + " | validade: " + l.getDataValidade()
                        + " | id do lote: " + l.getId());
            }
        }

        // 4. Consulta de alerta: com 7 dias de prazo, só o loteB (3 dias) deve aparecer
        System.out.println("\n--- Lotes vencendo em até " + Constantes.DIAS_ALERTA_VENCIMENTO + " dias ---");
        List<Lote> vencendo = loteDAO.listarVencendoEm(Constantes.DIAS_ALERTA_VENCIMENTO);
        for (Lote l : vencendo) {
            if (l.getProdutoId().equals(arroz.getId())) {
                System.out.println(l.getNomeProduto() + " | validade: " + l.getDataValidade());
            }
        }

        // 5. Testa a baixa: marca o loteA como descartado e confere a perda financeira
        loteDAO.marcarComoDescartado(loteA.getId());
        double perdaTotal = loteDAO.calcularPerdaFinanceiraTotal();
        System.out.println("\nLote A marcado como DESCARTADO.");
        System.out.println("Perda financeira acumulada no sistema: R$ " + perdaTotal);

        System.out.println("\nTeste concluído. Se a ordem e os valores acima baterem com o esperado, a camada de dados está OK.");
    }
}
