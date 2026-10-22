(function () {
    'use strict';

    const pagina = document.getElementById('paginaConsumo');
    const seletor = document.getElementById('produtoId');
    if (!pagina || !seletor) {
        return;
    }

    const elemento = (id) => document.getElementById(id);
    const btnLer = elemento('btnLerCodigo');
    const btnCancelar = elemento('btnCancelarLeitura');
    const areaCamera = elemento('areaCamera');
    const mensagemCamera = elemento('mensagemCamera');
    const mensagemLeitura = elemento('mensagemLeitura');
    const campoQuantidade = elemento('quantidade');

    function mostrarMensagem(texto, cor) {
        mensagemLeitura.className = 'alert alert-' + cor;
        mensagemLeitura.textContent = texto;
        mensagemLeitura.hidden = false;
    }

    function fecharCamera() {
        leitor.parar();
        areaCamera.hidden = true;
        btnLer.disabled = false;
    }

    const leitor = new LeitorCodigoBarras({
        video: elemento('videoCamera'),
        seletorCamera: elemento('seletorCamera'),
        dicaImagemPreta: elemento('dicaImagemPreta'),
        aoLer: (codigo) => {
            fecharCamera();
            const opcao = Array.from(seletor.options).find((o) => o.dataset.codigo === codigo);
            if (opcao) {
                seletor.value = opcao.value;
                atualizarMaximo();
                mostrarMensagem('Produto encontrado: ' + opcao.dataset.nome + '. Agora informe a quantidade.', 'success');
                campoQuantidade.focus();
                campoQuantidade.select();
            } else {
                mostrarMensagem('O código ' + codigo + ' não corresponde a nenhum produto com itens na despensa.',
                    'warning');
            }
        },
        aoErro: (mensagem) => {
            fecharCamera();
            mensagemCamera.textContent = mensagem;
            mensagemCamera.hidden = false;
            seletor.focus();
        }
    });

    function atualizarMaximo() {
        const opcao = seletor.options[seletor.selectedIndex];
        const disponivel = opcao ? opcao.dataset.disponivel : '';
        if (disponivel) {
            campoQuantidade.max = disponivel;
        } else {
            campoQuantidade.removeAttribute('max');
        }
    }

    seletor.addEventListener('change', atualizarMaximo);
    btnLer.addEventListener('click', () => {
        mensagemCamera.hidden = true;
        mensagemLeitura.hidden = true;
        areaCamera.hidden = false;
        btnLer.disabled = true;
        leitor.iniciar();
    });
    btnCancelar.addEventListener('click', fecharCamera);

    const secaoPrevia = elemento('secaoPrevia');
    if (secaoPrevia) {
        secaoPrevia.scrollIntoView({block: 'start'});
    }
})();
