(function (raiz, criar) {
    'use strict';
    const modulo = criar();
    if (typeof module === 'object' && module.exports) {
        module.exports = modulo;
    } else {
        raiz.EscolhaDeCamera = modulo;
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    const NOMES_VIRTUAIS = /obs|droidcam|virtual|iriun|manycam/i;
    const NOMES_TRASEIROS = /back|rear|traseira|environment/i;
    const LIMITE_IMAGEM_PRETA = 16;

    function ehVirtual(camera) {
        return NOMES_VIRTUAIS.test((camera && camera.label) || '');
    }

    function ehTraseira(camera) {
        return camera.facingMode === 'environment' || NOMES_TRASEIROS.test(camera.label || '');
    }

    function escolherCamera(cameras, idGuardado) {
        if (!cameras || cameras.length === 0) {
            return null;
        }
        if (idGuardado) {
            const guardada = cameras.find((camera) => camera.deviceId === idGuardado);
            if (guardada) {
                return guardada;
            }
        }
        const reais = cameras.filter((camera) => !ehVirtual(camera));
        return reais.find(ehTraseira) || reais[0] || cameras[0];
    }

    function nomeParaLista(camera, indice) {
        const nome = camera.label || 'Câmera ' + (indice + 1);
        return ehVirtual(camera) ? nome + ' (virtual)' : nome;
    }

    function luminanciaMedia(rgba) {
        if (!rgba || rgba.length < 4) {
            return 0;
        }
        let soma = 0;
        let pixels = 0;
        for (let i = 0; i + 3 < rgba.length; i += 4) {
            soma += 0.299 * rgba[i] + 0.587 * rgba[i + 1] + 0.114 * rgba[i + 2];
            pixels++;
        }
        return soma / pixels;
    }

    function imagemPreta(rgba) {
        return luminanciaMedia(rgba) < LIMITE_IMAGEM_PRETA;
    }

    return {ehVirtual, ehTraseira, escolherCamera, nomeParaLista, luminanciaMedia, imagemPreta};
});
