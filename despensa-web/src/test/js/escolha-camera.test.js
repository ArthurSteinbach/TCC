'use strict';

const {test} = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const escolha = require(path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'escolha-camera.js'));

const logitech = {deviceId: 'logitech', label: 'Logitech HD Webcam C270 (046d:0825)'};
const obs = {deviceId: 'obs', label: 'OBS Virtual Camera'};
const droidcam = {deviceId: 'droidcam', label: 'DroidCam Source 3'};
const iriun = {deviceId: 'iriun', label: 'Iriun Webcam'};
const manycam = {deviceId: 'manycam', label: 'ManyCam Virtual Webcam'};
const frontal = {deviceId: 'frontal', label: 'camera2 1, facing front'};
const traseira = {deviceId: 'traseira', label: 'camera2 0, facing back'};

test('com câmeras virtuais antes da webcam, escolhe a webcam de verdade', () => {
    assert.equal(escolha.escolherCamera([obs, droidcam, logitech], null), logitech);
});

test('sem câmera virtual, escolhe a primeira câmera', () => {
    const integrada = {deviceId: 'integrada', label: 'Integrated Camera'};
    assert.equal(escolha.escolherCamera([integrada, logitech], null), integrada);
});

test('só com câmeras virtuais, usa a primeira delas', () => {
    assert.equal(escolha.escolherCamera([droidcam, obs], null), droidcam);
});

test('no celular, prefere a traseira pelo nome mesmo listada depois da frontal', () => {
    assert.equal(escolha.escolherCamera([frontal, traseira], null), traseira);
});

test('reconhece a traseira pelo nome em português', () => {
    const traseiraPt = {deviceId: 'tp', label: 'Câmera traseira'};
    assert.equal(escolha.escolherCamera([frontal, traseiraPt], null), traseiraPt);
});

test('prefere a traseira pelo facingMode environment', () => {
    const a = {deviceId: 'a', label: 'Camera A'};
    const b = {deviceId: 'b', label: 'Camera B', facingMode: 'environment'};
    assert.equal(escolha.escolherCamera([a, b], null), b);
});

test('ignora uma câmera virtual mesmo que ela se diga traseira', () => {
    const virtualTraseira = {deviceId: 'vt', label: 'DroidCam Back Camera'};
    assert.equal(escolha.escolherCamera([virtualTraseira, logitech], null), logitech);
});

test('sem câmera traseira, usa a primeira que não é virtual', () => {
    assert.equal(escolha.escolherCamera([obs, frontal, logitech], null), frontal);
});

test('a câmera escolhida antes pelo usuário vence, mesmo sendo virtual', () => {
    assert.equal(escolha.escolherCamera([logitech, obs], 'obs'), obs);
});

test('a câmera escolhida antes vence a traseira', () => {
    assert.equal(escolha.escolherCamera([frontal, traseira], 'frontal'), frontal);
});

test('escolha guardada de uma câmera que não existe mais segue a ordem normal', () => {
    assert.equal(escolha.escolherCamera([obs, logitech], 'camera-removida'), logitech);
});

test('lista vazia não escolhe nada', () => {
    assert.equal(escolha.escolherCamera([], null), null);
    assert.equal(escolha.escolherCamera(undefined, null), null);
});

test('sem nomes (antes da permissão), fica com a primeira câmera', () => {
    const semNome1 = {deviceId: '', label: ''};
    const semNome2 = {deviceId: '', label: ''};
    assert.equal(escolha.escolherCamera([semNome1, semNome2], ''), semNome1);
});

test('reconhece como virtual OBS, DroidCam, Virtual, Iriun e ManyCam sem diferenciar maiúsculas', () => {
    for (const camera of [obs, droidcam, iriun, manycam, {label: 'my virtual cam'}, {label: 'obs-camera'}]) {
        assert.equal(escolha.ehVirtual(camera), true, camera.label);
    }
    for (const camera of [logitech, frontal, traseira, {label: ''}, {}]) {
        assert.equal(escolha.ehVirtual(camera), false, camera.label);
    }
});

test('nome na lista marca as virtuais e numera as câmeras sem nome', () => {
    assert.equal(escolha.nomeParaLista(obs, 0), 'OBS Virtual Camera (virtual)');
    assert.equal(escolha.nomeParaLista(logitech, 1), 'Logitech HD Webcam C270 (046d:0825)');
    assert.equal(escolha.nomeParaLista({deviceId: 'x', label: ''}, 2), 'Câmera 3');
});

test('imagem toda preta ou quase preta é detectada', () => {
    assert.equal(escolha.imagemPreta(new Uint8ClampedArray(32 * 24 * 4)), true);
    const quasePreta = new Uint8ClampedArray(32 * 24 * 4).fill(10);
    assert.equal(escolha.imagemPreta(quasePreta), true);
});

test('imagem com luz normal não é considerada preta', () => {
    const clara = new Uint8ClampedArray(32 * 24 * 4).fill(120);
    assert.equal(escolha.imagemPreta(clara), false);
    assert.ok(Math.abs(escolha.luminanciaMedia(clara) - 120) < 0.001);
});
