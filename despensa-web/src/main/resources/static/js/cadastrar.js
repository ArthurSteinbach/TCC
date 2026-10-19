(function () {
    'use strict';

    const pagina = document.getElementById('paginaCadastro');
    if (!pagina) {
        return;
    }

    const urlConsulta = pagina.dataset.urlConsulta;
    const hoje = pagina.dataset.hoje;
    const limiteUnidades = parseInt(pagina.dataset.limiteUnidades, 10) || 50;

    const elemento = (id) => document.getElementById(id);
    const campoCodigo = elemento('codigoBarras');
    const btnLer = elemento('btnLerCodigo');
    const btnCancelar = elemento('btnCancelarLeitura');
    const btnBuscar = elemento('btnBuscarCodigo');
    const areaCamera = elemento('areaCamera');
    const mensagemCamera = elemento('mensagemCamera');
    const mensagemConsulta = elemento('mensagemConsulta');
    const secaoProduto = elemento('secaoProduto');
    const secaoLote = elemento('secaoLote');
    const blocoExistente = elemento('produtoExistente');
    const nomeExistente = elemento('nomeProdutoExistente');
    const camposNovo = elemento('camposProdutoNovo');
    const campoNome = elemento('nome');
    const campoCategoria = elemento('categoria');
    const campoQuantidade = elemento('quantidade');
    const checkMesmaValidade = elemento('mesmaValidade');
    const blocoValidadeUnica = elemento('validadeUnica');
    const campoValidade = elemento('validade');
    const blocoValidadesIndividuais = elemento('validadesIndividuais');
    const listaValidades = elemento('listaValidades');
    const avisoLimite = elemento('avisoLimiteUnidades');

    const CORES = {
        EXISTENTE: 'success',
        ENCONTRADO: 'success',
        NAO_ENCONTRADO: 'warning',
        FALHA: 'warning',
        SEM_CONSULTA: 'info',
        CODIGO_VAZIO: 'danger'
    };

    let codigoConsultado = campoCodigo.value.trim() || null;

    const leitor = new LeitorCodigoBarras({
        video: elemento('videoCamera'),
        aoLer: (codigo) => {
            fecharCamera();
            campoCodigo.value = codigo;
            consultar(codigo);
        },
        aoErro: (mensagem) => {
            fecharCamera();
            mensagemCamera.textContent = mensagem;
            mensagemCamera.hidden = false;
            campoCodigo.focus();
        }
    });

    function abrirCamera() {
        mensagemCamera.hidden = true;
        areaCamera.hidden = false;
        btnLer.disabled = true;
        leitor.iniciar();
    }

    function fecharCamera() {
        leitor.parar();
        areaCamera.hidden = true;
        btnLer.disabled = false;
    }

    function mostrarMensagem(texto, cor) {
        mensagemConsulta.className = 'alert mt-3 mb-0 alert-' + cor;
        mensagemConsulta.textContent = texto;
        mensagemConsulta.hidden = false;
    }

    function consultar(codigoDigitado) {
        const codigo = (codigoDigitado || '').trim();
        if (!codigo) {
            mostrarMensagem('Informe o código de barras.', 'danger');
            campoCodigo.focus();
            return;
        }

        mostrarMensagem('Buscando o produto...', 'secondary');
        btnBuscar.disabled = true;

        fetch(urlConsulta + '?codigo=' + encodeURIComponent(codigo), {
            headers: {'Accept': 'application/json'},
            credentials: 'same-origin'
        })
            .then((resposta) => {
                if (resposta.redirected) {
                    throw new Error('sessao');
                }
                const tipo = resposta.headers.get('Content-Type') || '';
                if (!resposta.ok || tipo.indexOf('application/json') === -1) {
                    throw new Error('falha');
                }
                return resposta.json();
            })
            .then((resultado) => aplicarResultado(codigo, resultado))
            .catch((erro) => {
                if (erro.message === 'sessao') {
                    mostrarMensagem('Sua sessão expirou. Atualize a página e entre de novo.', 'danger');
                    return;
                }
                aplicarResultado(codigo, {
                    situacao: 'FALHA',
                    mensagem: 'Não foi possível consultar o código agora. Preencha o nome e a categoria.'
                });
            })
            .finally(() => {
                btnBuscar.disabled = false;
            });
    }

    function aplicarResultado(codigo, resultado) {
        if (resultado.situacao === 'CODIGO_VAZIO') {
            mostrarMensagem(resultado.mensagem, CORES.CODIGO_VAZIO);
            return;
        }

        codigoConsultado = codigo;
        mostrarMensagem(resultado.mensagem, CORES[resultado.situacao] || 'info');

        const existente = resultado.situacao === 'EXISTENTE';
        blocoExistente.hidden = !existente;
        camposNovo.hidden = existente;
        campoNome.disabled = existente;
        campoCategoria.disabled = existente;

        if (existente) {
            nomeExistente.textContent = resultado.nome;
        } else if (resultado.situacao === 'ENCONTRADO') {
            campoNome.value = resultado.nome || '';
            campoCategoria.value = resultado.categoria || '';
        }

        secaoProduto.hidden = false;
        secaoLote.hidden = false;
        (existente || campoNome.value ? campoQuantidade : campoNome).focus();
    }

    function codigoAlterado() {
        if (campoCodigo.value.trim() === codigoConsultado) {
            return;
        }
        codigoConsultado = null;
        secaoProduto.hidden = true;
        secaoLote.hidden = true;
        mensagemConsulta.hidden = true;
        campoNome.value = '';
        campoCategoria.value = '';
    }

    function lerQuantidade() {
        const quantidade = parseInt(campoQuantidade.value, 10);
        return isNaN(quantidade) || quantidade < 1 ? 1 : quantidade;
    }

    function atualizarValidades() {
        const mesma = checkMesmaValidade.checked;
        blocoValidadeUnica.hidden = !mesma;
        campoValidade.disabled = !mesma;
        blocoValidadesIndividuais.hidden = mesma;

        const camposAtuais = Array.from(listaValidades.querySelectorAll('input'));
        if (mesma) {
            camposAtuais.forEach((campo) => {
                campo.disabled = true;
            });
            return;
        }

        const valores = camposAtuais.map((campo) => campo.value);
        const pedida = lerQuantidade();
        const quantidade = Math.min(pedida, limiteUnidades);
        avisoLimite.hidden = pedida <= limiteUnidades;

        listaValidades.textContent = '';
        for (let i = 0; i < quantidade; i++) {
            const id = 'validade-' + (i + 1);
            const linha = document.createElement('div');
            linha.className = 'mb-2';

            const rotulo = document.createElement('label');
            rotulo.className = 'form-label';
            rotulo.htmlFor = id;
            rotulo.textContent = 'Unidade ' + (i + 1);

            const campo = document.createElement('input');
            campo.type = 'date';
            campo.className = 'form-control form-control-lg';
            campo.name = 'validades';
            campo.id = id;
            campo.min = hoje;
            campo.required = true;
            campo.value = valores[i] || campoValidade.value || '';

            linha.append(rotulo, campo);
            listaValidades.append(linha);
        }
    }

    btnLer.addEventListener('click', abrirCamera);
    btnCancelar.addEventListener('click', fecharCamera);
    btnBuscar.addEventListener('click', () => consultar(campoCodigo.value));
    campoCodigo.addEventListener('keydown', (evento) => {
        if (evento.key === 'Enter') {
            evento.preventDefault();
            consultar(campoCodigo.value);
        }
    });
    campoCodigo.addEventListener('input', codigoAlterado);
    checkMesmaValidade.addEventListener('change', atualizarValidades);
    campoQuantidade.addEventListener('input', () => {
        if (!checkMesmaValidade.checked) {
            atualizarValidades();
        }
    });
    window.addEventListener('pagehide', () => leitor.parar());

    atualizarValidades();
})();
