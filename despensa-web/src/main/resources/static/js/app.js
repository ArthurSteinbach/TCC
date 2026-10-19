document.addEventListener('submit', function (evento) {
    var mensagem = evento.target.getAttribute('data-confirmacao');
    if (mensagem && !window.confirm(mensagem)) {
        evento.preventDefault();
    }
});
