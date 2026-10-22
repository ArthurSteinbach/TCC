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
