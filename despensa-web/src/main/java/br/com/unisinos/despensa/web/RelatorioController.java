package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.LoteDoProduto;
import br.com.unisinos.despensa.seguranca.UsuarioLogado;
import br.com.unisinos.despensa.service.LoteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class RelatorioController {

    private final LoteService loteService;

    public RelatorioController(LoteService loteService) {
        this.loteService = loteService;
    }

    @GetMapping("/relatorio")
    public String relatorio(@AuthenticationPrincipal UsuarioLogado usuario, Model model) {
        List<ItemRelatorio> descartados = loteService.listarDescartados(usuario.getId()).stream()
                .map(ItemRelatorio::de)
                .toList();

        model.addAttribute("menu", "relatorio");
        model.addAttribute("descartados", descartados);
        model.addAttribute("perdaTotal", descartados.stream().mapToDouble(ItemRelatorio::valorTotal).sum());
        return "relatorio";
    }

    public record ItemRelatorio(String nomeProduto, double quantidade, double valorTotal, LocalDateTime dataBaixa) {

        static ItemRelatorio de(LoteDoProduto loteDoProduto) {
            Lote lote = loteDoProduto.lote();
            return new ItemRelatorio(loteDoProduto.nomeProduto(), lote.getQuantidade(), lote.getValorTotal(),
                    lote.getDataBaixa());
        }

        public String quantidadeFormatada() {
            return Formatos.quantidade(quantidade);
        }
    }
}
