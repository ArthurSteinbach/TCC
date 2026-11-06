document.addEventListener('submit', function (evento) {
    var formulario = evento.target;
    var mensagem = formulario.getAttribute('data-confirmacao');
    if (!mensagem) {
        return;
    }
    var campoQuantidade = formulario.querySelector('[name="quantidade"]');
    if (campoQuantidade) {
        mensagem = mensagem.replace('{quantidade}', campoQuantidade.value);
    }
    if (!window.confirm(mensagem)) {
        evento.preventDefault();
    }
});

document.addEventListener('submit', function (evento) {
    var formulario = evento.target;
    var textoEnviando = formulario.getAttribute('data-envio-unico');
    if (textoEnviando === null) {
        return;
    }
    if (formulario.hasAttribute('data-enviado')) {
        evento.preventDefault();
        return;
    }
    if (evento.defaultPrevented) {
        return;
    }
    formulario.setAttribute('data-enviado', '');
    var botao = formulario.querySelector('button[type="submit"]');
    if (botao) {
        botao.setAttribute('data-texto-original', botao.textContent);
        botao.disabled = true;
        botao.textContent = textoEnviando;
    }
});

window.addEventListener('pageshow', function (evento) {
    if (!evento.persisted) {
        return;
    }
    document.querySelectorAll('form[data-enviado]').forEach(function (formulario) {
        formulario.removeAttribute('data-enviado');
        var botao = formulario.querySelector('button[type="submit"][data-texto-original]');
        if (botao) {
            botao.disabled = false;
            botao.textContent = botao.getAttribute('data-texto-original');
        }
    });
});
