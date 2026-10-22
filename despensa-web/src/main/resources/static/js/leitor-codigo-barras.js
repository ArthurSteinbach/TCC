(function (global) {
    'use strict';

    const LEITURAS_IGUAIS_NECESSARIAS = 2;
    const SEGUNDOS_ESCUROS_PARA_DICA = 3;
    const CHAVE_CAMERA_GUARDADA = 'despensa.cameraEscolhida';

    const MENSAGENS = {
        inseguro: 'A câmera só funciona em conexão segura (HTTPS) ou em localhost. Digite o código abaixo.',
        semSuporte: 'Este navegador não permite usar a câmera. Digite o código abaixo.',
        semBiblioteca: 'Não foi possível carregar o leitor de código de barras. Verifique a internet ou digite o código abaixo.',
        negado: 'A permissão para usar a câmera foi negada. Libere a câmera nas configurações do navegador ou digite o código abaixo.',
        semCamera: 'Nenhuma câmera foi encontrada neste aparelho. Digite o código abaixo.',
        ocupada: 'A câmera está sendo usada por outro aplicativo. Feche-o, escolha outra câmera na lista ou digite o código abaixo.',
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

    function lerCameraGuardada() {
        try {
            return global.localStorage.getItem(CHAVE_CAMERA_GUARDADA);
        } catch (e) {
            return null;
        }
    }

    function guardarCamera(deviceId) {
        try {
            global.localStorage.setItem(CHAVE_CAMERA_GUARDADA, deviceId);
        } catch (e) {
            return;
        }
    }

    function liberar(stream) {
        if (stream) {
            stream.getTracks().forEach((trilha) => trilha.stop());
        }
    }

    function permissaoNegada(erro) {
        return erro && (erro.name === 'NotAllowedError' || erro.name === 'PermissionDeniedError'
            || erro.name === 'SecurityError');
    }

    class LeitorCodigoBarras {

        constructor({video, seletorCamera, dicaImagemPreta, aoLer, aoErro}) {
            this.video = video;
            this.seletorCamera = seletorCamera || null;
            this.dicaImagemPreta = dicaImagemPreta || null;
            this.aoLer = aoLer;
            this.aoErro = aoErro;
            this.stream = null;
            this.controles = null;
            this.vigia = null;
            this.cameraEscolhida = null;
            this.sessao = 0;

            if (this.seletorCamera) {
                this.seletorCamera.addEventListener('change', () => this.trocarCamera(this.seletorCamera.value));
            }
            global.addEventListener('pagehide', () => this.parar());
        }

        static verificarSuporte() {
            if (!global.isSecureContext) {
                return MENSAGENS.inseguro;
            }
            if (!navigator.mediaDevices || typeof navigator.mediaDevices.getUserMedia !== 'function'
                || typeof navigator.mediaDevices.enumerateDevices !== 'function') {
                return MENSAGENS.semSuporte;
            }
            if (typeof global.ZXingBrowser === 'undefined' || typeof global.EscolhaDeCamera === 'undefined') {
                return MENSAGENS.semBiblioteca;
            }
            return null;
        }

        async iniciar() {
            this.parar();
            const problema = LeitorCodigoBarras.verificarSuporte();
            if (problema) {
                this.aoErro(problema);
                return false;
            }

            const sessao = ++this.sessao;
            try {
                const cameras = await this.listarCameras();
                if (sessao !== this.sessao) {
                    return false;
                }
                if (cameras.length === 0) {
                    this.sessao++;
                    this.aoErro(MENSAGENS.semCamera);
                    return false;
                }
                const escolhida = cameras.find((c) => c.deviceId && c.deviceId === this.cameraEscolhida)
                    || global.EscolhaDeCamera.escolherCamera(cameras, lerCameraGuardada());
                this.mostrarLista(cameras, escolhida);
                return await this.abrir(escolhida, sessao);
            } catch (erro) {
                if (sessao !== this.sessao) {
                    return false;
                }
                this.parar();
                this.aoErro(mensagemDoErro(erro));
                return false;
            }
        }

        async trocarCamera(deviceId) {
            if (!deviceId) {
                return;
            }
            guardarCamera(deviceId);
            this.cameraEscolhida = deviceId;
            if (this.stream) {
                await this.iniciar();
            }
        }

        parar() {
            this.sessao++;
            if (this.vigia) {
                global.clearInterval(this.vigia);
                this.vigia = null;
            }
            if (this.dicaImagemPreta) {
                this.dicaImagemPreta.hidden = true;
            }
            const controles = this.controles;
            this.controles = null;
            if (controles) {
                try {
                    controles.stop();
                } catch (erro) {
                    console.warn('Falha ao parar o leitor de código de barras', erro);
                }
            }
            liberar(this.stream);
            this.stream = null;
            if (this.video) {
                this.video.pause();
                this.video.srcObject = null;
            }
        }

        async listarCameras() {
            let cameras = await this.camerasDoAparelho();
            if (cameras.length === 0 || cameras.every((camera) => !camera.label)) {
                try {
                    liberar(await navigator.mediaDevices.getUserMedia({audio: false, video: true}));
                } catch (erro) {
                    if (permissaoNegada(erro) || erro.name === 'NotFoundError') {
                        throw erro;
                    }
                }
                cameras = await this.camerasDoAparelho();
            }
            return cameras;
        }

        async camerasDoAparelho() {
            const dispositivos = await navigator.mediaDevices.enumerateDevices();
            return dispositivos
                .filter((dispositivo) => dispositivo.kind === 'videoinput')
                .map((dispositivo) => {
                    const capacidades = typeof dispositivo.getCapabilities === 'function'
                        ? dispositivo.getCapabilities() : null;
                    const traseira = capacidades && Array.isArray(capacidades.facingMode)
                        && capacidades.facingMode.includes('environment');
                    return {
                        deviceId: dispositivo.deviceId,
                        label: dispositivo.label,
                        facingMode: traseira ? 'environment' : undefined
                    };
                });
        }

        mostrarLista(cameras, escolhida) {
            if (!this.seletorCamera) {
                return;
            }
            this.seletorCamera.textContent = '';
            cameras.forEach((camera, indice) => {
                const opcao = document.createElement('option');
                opcao.value = camera.deviceId;
                opcao.textContent = global.EscolhaDeCamera.nomeParaLista(camera, indice);
                opcao.selected = camera === escolhida;
                this.seletorCamera.append(opcao);
            });
            const bloco = this.seletorCamera.closest('.seletor-camera') || this.seletorCamera;
            bloco.hidden = cameras.length < 2;
        }

        async abrir(camera, sessao) {
            const restricoesDeVideo = camera.deviceId
                ? {deviceId: {exact: camera.deviceId}, width: {ideal: 1280}, height: {ideal: 720}}
                : {facingMode: {ideal: 'environment'}};
            const stream = await navigator.mediaDevices.getUserMedia({audio: false, video: restricoesDeVideo});
            if (sessao !== this.sessao) {
                liberar(stream);
                return false;
            }
            this.stream = stream;
            this.cameraEscolhida = camera.deviceId || null;

            let ultimoCodigo = null;
            let repeticoes = 0;
            const leitor = new global.ZXingBrowser.BrowserMultiFormatReader(new Map());
            leitor.possibleFormats = [global.ZXingBrowser.BarcodeFormat.EAN_13];

            const controles = await leitor.decodeFromStream(stream, this.video, (resultado) => {
                if (!resultado || sessao !== this.sessao) {
                    return;
                }
                const codigo = resultado.getText();
                repeticoes = codigo === ultimoCodigo ? repeticoes + 1 : 1;
                ultimoCodigo = codigo;
                if (repeticoes < LEITURAS_IGUAIS_NECESSARIAS) {
                    return;
                }
                this.parar();
                if (typeof navigator.vibrate === 'function') {
                    navigator.vibrate(200);
                }
                this.aoLer(codigo);
            });

            if (sessao !== this.sessao) {
                controles.stop();
                liberar(stream);
                return false;
            }
            this.controles = controles;
            this.vigiarImagemPreta(sessao);
            return true;
        }

        vigiarImagemPreta(sessao) {
            if (!this.dicaImagemPreta) {
                return;
            }
            const tela = document.createElement('canvas');
            tela.width = 32;
            tela.height = 24;
            const contexto = typeof tela.getContext === 'function'
                ? tela.getContext('2d', {willReadFrequently: true}) : null;
            if (!contexto) {
                return;
            }
            let segundosEscuros = 0;

            this.vigia = global.setInterval(() => {
                if (sessao !== this.sessao || this.video.readyState < 2) {
                    return;
                }
                try {
                    contexto.drawImage(this.video, 0, 0, tela.width, tela.height);
                    const pixels = contexto.getImageData(0, 0, tela.width, tela.height).data;
                    segundosEscuros = global.EscolhaDeCamera.imagemPreta(pixels) ? segundosEscuros + 1 : 0;
                    this.dicaImagemPreta.hidden = segundosEscuros < SEGUNDOS_ESCUROS_PARA_DICA;
                } catch (erro) {
                    global.clearInterval(this.vigia);
                    this.vigia = null;
                }
            }, 1000);
        }
    }

    global.LeitorCodigoBarras = LeitorCodigoBarras;
})(window);
