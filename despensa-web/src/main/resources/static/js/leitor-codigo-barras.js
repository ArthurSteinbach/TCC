(function (global) {
    'use strict';

    const LEITURAS_IGUAIS_NECESSARIAS = 2;

    const MENSAGENS = {
        inseguro: 'A câmera só funciona em conexão segura (HTTPS) ou em localhost. Digite o código abaixo.',
        semSuporte: 'Este navegador não permite usar a câmera. Digite o código abaixo.',
        semBiblioteca: 'Não foi possível carregar o leitor de código de barras. Verifique a internet ou digite o código abaixo.',
        negado: 'A permissão para usar a câmera foi negada. Libere a câmera nas configurações do navegador ou digite o código abaixo.',
        semCamera: 'Nenhuma câmera foi encontrada neste aparelho. Digite o código abaixo.',
        ocupada: 'A câmera está sendo usada por outro aplicativo. Feche-o e tente de novo, ou digite o código abaixo.',
        generico: 'Não foi possível abrir a câmera. Tente de novo ou digite o código abaixo.'
    };

    function mensagemDoErro(erro) {
        switch (erro && erro.name) {
            case 'NotAllowedError':
            case 'PermissionDeniedError':
            case 'SecurityError':
                return MENSAGENS.negado;
            case 'NotFoundError':
            case 'DevicesNotFoundError':
            case 'OverconstrainedError':
                return MENSAGENS.semCamera;
            case 'NotReadableError':
            case 'TrackStartError':
            case 'AbortError':
                return MENSAGENS.ocupada;
            default:
                return MENSAGENS.generico;
        }
    }

    class LeitorCodigoBarras {

        constructor({video, aoLer, aoErro}) {
            this.video = video;
            this.aoLer = aoLer;
            this.aoErro = aoErro;
            this.controles = null;
            this.sessao = 0;
        }

        static verificarSuporte() {
            if (!global.isSecureContext) {
                return MENSAGENS.inseguro;
            }
            if (!navigator.mediaDevices || typeof navigator.mediaDevices.getUserMedia !== 'function') {
                return MENSAGENS.semSuporte;
            }
            if (typeof global.ZXingBrowser === 'undefined') {
                return MENSAGENS.semBiblioteca;
            }
            return null;
        }

        iniciar() {
            this.parar();
            const problema = LeitorCodigoBarras.verificarSuporte();
            if (problema) {
                this.aoErro(problema);
                return Promise.resolve(false);
            }

            const sessao = ++this.sessao;
            let ultimoCodigo = null;
            let repeticoes = 0;

            const leitor = new global.ZXingBrowser.BrowserMultiFormatReader(new Map());
            leitor.possibleFormats = [global.ZXingBrowser.BarcodeFormat.EAN_13];

            const restricoes = {audio: false, video: {facingMode: {ideal: 'environment'}}};

            return leitor.decodeFromConstraints(restricoes, this.video, (resultado, erro, controles) => {
                if (!resultado || sessao !== this.sessao) {
                    return;
                }
                const codigo = resultado.getText();
                repeticoes = codigo === ultimoCodigo ? repeticoes + 1 : 1;
                ultimoCodigo = codigo;
                if (repeticoes < LEITURAS_IGUAIS_NECESSARIAS) {
                    return;
                }
                this.sessao++;
                controles.stop();
                this.controles = null;
                if (typeof navigator.vibrate === 'function') {
                    navigator.vibrate(200);
                }
                this.aoLer(codigo);
            }).then((controles) => {
                if (sessao !== this.sessao) {
                    controles.stop();
                    return false;
                }
                this.controles = controles;
                return true;
            }).catch((erro) => {
                if (sessao !== this.sessao) {
                    return false;
                }
                this.sessao++;
                this.aoErro(mensagemDoErro(erro));
                return false;
            });
        }

        parar() {
            this.sessao++;
            if (this.controles) {
                this.controles.stop();
                this.controles = null;
            }
        }
    }

    global.LeitorCodigoBarras = LeitorCodigoBarras;
})(window);
