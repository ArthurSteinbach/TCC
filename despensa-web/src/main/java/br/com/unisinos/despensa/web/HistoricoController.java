package br.com.unisinos.despensa.web;

import br.com.unisinos.despensa.model.Lote;
import br.com.unisinos.despensa.model.LoteDoProduto;
import br.com.unisinos.despensa.model.StatusLote;
import br.com.unisinos.despensa.seguranca.UsuarioLogado;
import br.com.unisinos.despensa.service.LoteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Map;

@Controller
@RequestMapping("/historico")
public class HistoricoController {

    private static final Map<String, StatusLote> FILTROS = Map.of(
            "consumidos", StatusLote.CONSUMIDO,
            "descartados", StatusLote.DESCARTADO);

    private final LoteService loteService;

    public HistoricoController(LoteService loteService) {
        this.loteService = loteService;
    }

    @GetMapping
    public String historico(@AuthenticationPrincipal UsuarioLogado usuario,
                            @RequestParam(defaultValue = "") String situacao, Model model) {
        String filtro = FILTROS.containsKey(situacao) ? situacao : "";
        model.addAttribute("menu", "historico");
        model.addAttribute("filtro", filtro);
        model.addAttribute("itens", loteService.listarHistorico(usuario.getId(), FILTROS.get(filtro)).stream()
                .map(ItemHistorico::de)
                .toList());
        return "historico";
    }

    public record ItemHistorico(String produtoId, String loteId, String nomeProduto, double quantidade,
                                double valorTotal, StatusLote status, LocalDateTime dataBaixa) {

        static ItemHistorico de(LoteDoProduto loteDoProduto) {
            Lote lote = loteDoProduto.lote();
            return new ItemHistorico(loteDoProduto.produtoId(), lote.getId(), loteDoProduto.nomeProduto(),
                    lote.getQuantidade(), lote.getValorTotal(), lote.getStatus(), lote.getDataBaixa());
        }

        public String situacao() {
            return status == StatusLote.DESCARTADO ? "Descartado" : "Consumido";
        }

        public boolean descartado() {
            return status == StatusLote.DESCARTADO;
        }

        public String quantidadeFormatada() {
            return Formatos.quantidade(quantidade);
        }
    }
}
