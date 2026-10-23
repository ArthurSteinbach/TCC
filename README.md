# Controle de Despensa

Sistema de controle de estoque doméstico desenvolvido como Trabalho de Conclusão de Curso (TCC) na
Unisinos, por Arthur Almeida Steinbach.

O objetivo é reduzir o desperdício de alimentos. O sistema registra cada compra com a sua data de
validade e aplica a regra **FEFO** (*First to Expire, First Out*): o que vence primeiro sai primeiro. Ele
avisa sobre os itens que estão vencendo, registra o que foi consumido ou jogado fora e calcula quanto
dinheiro foi perdido com o desperdício.

**Versão publicada:** <https://despensa-tcc.onrender.com>

> A versão publicada usa o plano gratuito do Render. Depois de 15 minutos sem acesso, o serviço "dorme"
> e o primeiro acesso seguinte pode levar cerca de um minuto para responder.

## As duas versões

O repositório guarda as duas versões do sistema, cada uma em sua pasta, como projetos Maven
independentes:

| Pasta | Versão | Situação |
|---|---|---|
| [`controle-despensa/`](controle-despensa) | Primeira versão: aplicativo **desktop** (Java Swing + SQLite), desenvolvida no TCC I | Mantida como histórico, sem alterações |
| [`despensa-web/`](despensa-web) | Versão atual: aplicação **web responsiva**, pensada primeiro para o celular | Em uso e publicada |

A versão web reaproveita as regras de negócio da versão desktop e acrescenta login com várias contas,
leitura do código de barras pela câmera do celular direto no navegador e publicação na internet.

## Funcionalidades (versão web)

- **Conta de usuário:** cadastro e login com e-mail e senha. Cada pessoa só vê e altera a própria despensa.
- **Despensa:** lista dos itens na ordem FEFO, com cores para os vencidos (vermelho claro) e para os que
  vencem em até 7 dias (amarelo claro), o aviso de itens em alerta e o total de perdas.
- **Cadastrar:** leitura do código de barras pela câmera ou digitação manual. Se o produto for novo, o
  nome e a categoria são buscados na base pública Open Food Facts. A compra pode ter uma validade para
  todos os itens ou uma validade por unidade.
- **Consumir:** escolha do produto (pela lista ou pela câmera) e da quantidade, com uma prévia de quais
  lotes serão usados pela regra FEFO antes de confirmar.
- **Descartar:** descarte do lote inteiro ou de parte dele. O valor descartado entra no total de perdas.
- **Alertas:** itens vencidos ou que vencem em até 7 dias.
- **Histórico:** tudo o que foi consumido ou descartado, com filtro e a opção de desfazer.
- **Relatório:** total perdido, total consumido, total descartado e percentual de desperdício.
- **Produtos:** edição de nome e categoria e exclusão de produtos sem lotes.
- **Notificações:** avisos por e-mail dos itens que entram nos 7 dias antes de vencer e dos que vencem,
  no máximo uma vez por dia. Na tela é possível ligar ou desligar os avisos, escolher outro e-mail (que só
  passa a valer depois de confirmado por um link) e enviar um e-mail de teste. Todo aviso tem um link para
  descadastrar.

## Tecnologias

**Versão web (`despensa-web`)**

- Java 21, Spring Boot 4.1 (Spring Web MVC, Spring Security, Spring Data MongoDB, Validation)
- Thymeleaf (páginas geradas no servidor) e Bootstrap 5.3
- MongoDB Atlas (banco de dados na nuvem)
- ZXing (`@zxing/browser`) para ler códigos de barras EAN-13 pela câmera
- API pública Open Food Facts para buscar nome e categoria dos produtos
- API HTTP de e-mail transacional da Brevo para enviar os avisos, disparada uma vez por dia por um
  workflow do GitHub Actions
- JUnit 5, Mockito e Spring Security Test; os testes da lógica de câmera em JavaScript usam o executor de
  testes do Node.js
- Docker e Render para a publicação com HTTPS

**Versão desktop (`controle-despensa`)**

- Java 21, Swing e SQLite (JDBC)
- ZXing e webcam-capture para a leitura de código de barras
- JUnit 5

## Como rodar a versão web no computador

### Requisitos

- **JDK 21 ou mais novo**, com a variável `JAVA_HOME` apontando para ele. O Maven não precisa ser
  instalado: o projeto usa o Maven Wrapper (`mvnw.cmd`).
- **Um banco no MongoDB Atlas** e a sua *connection string*. No Atlas, em *Network Access*, o IP do
  computador precisa estar liberado.
- **Internet**, para baixar as dependências na primeira execução e acessar o Atlas.

### 1. Criar a variável de ambiente `MONGODB_URI`

A connection string fica **somente** nessa variável de ambiente. Ela nunca é escrita no código nem em
arquivos do repositório.

No Windows:

1. Aperte **Win**, digite **"variáveis de ambiente"** e abra **"Editar as variáveis de ambiente para sua conta"**.
2. Em **Variáveis de usuário**, clique em **Novo...**
3. Nome da variável: `MONGODB_URI`. Valor: a connection string do Atlas, com a sua senha.
4. Confirme com **OK** e **feche e abra de novo** o terminal ou o editor, para que eles enxerguem a variável.

### 2. Rodar

Dê dois cliques em `despensa-web\rodar.cmd` ou, num terminal:

```powershell
cd despensa-web
.\rodar.cmd
```

Depois abra <http://localhost:8080> no navegador. O `rodar.cmd` avisa e não inicia se a variável
`MONGODB_URI` não estiver definida. O nome do banco usado é `despensa`.

> A câmera do navegador só funciona em **HTTPS** ou em **localhost**. No computador, use
> `http://localhost:8080`. No celular, use a versão publicada.

## Como rodar a versão desktop

```powershell
cd controle-despensa
.\rodar.cmd
```

Ela não precisa de banco na nuvem: os dados ficam no arquivo `despensa.db` (SQLite), criado na própria
pasta.

## Testes

Os testes não usam o MongoDB Atlas, a internet nem a Brevo: o banco é substituído por repositórios em
memória, a Open Food Facts por respostas simuladas e o envio de e-mail por um enviador falso.

```powershell
cd despensa-web
.\mvnw.cmd test
```

| Projeto | Testes |
|---|---|
| `despensa-web`, com o Node.js instalado | 334 |
| `despensa-web`, sem o Node.js | 317 (os 17 testes de JavaScript não rodam e não quebram o build) |
| `controle-despensa` | 51 |

Os testes da lógica de escolha da câmera estão em `despensa-web/src/test/js` e rodam com o Node.js
(versão 20 ou mais nova) dentro do `mvnw.cmd test`. Para rodar só esses testes:

```powershell
cd despensa-web
node --test src/test/js/escolha-camera.test.js
```

Para a versão desktop:

```powershell
cd controle-despensa
.\mvnw.cmd test
```

## Publicação

A versão web é publicada no [Render](https://render.com) a partir do `Dockerfile` em `despensa-web/`. O
Render compila o projeto, roda o `.jar` com a memória ajustada para o plano gratuito e entrega o site em
HTTPS. A variável `MONGODB_URI` é configurada no painel do Render, nunca no repositório. O endereço
`/saude` responde `OK` quando o serviço está no ar e não mostra nenhum dado.

## Avisos por e-mail

Os avisos de vencimento são enviados pela API HTTP da [Brevo](https://www.brevo.com), porque o plano
gratuito do Render bloqueia o envio por SMTP. Como o app "dorme" no plano gratuito, o disparo diário não
fica dentro dele: o workflow [`.github/workflows/alertas-vencimento.yml`](.github/workflows/alertas-vencimento.yml)
roda todo dia às 8h de Brasília (11h UTC), acorda o app e chama `POST /tarefas/enviar-alertas` com o
cabeçalho `X-Alertas-Token`. Ele também pode ser rodado à mão na aba **Actions** do GitHub.

Regras do envio:

- No máximo um e-mail por pessoa por dia, e só quando há novidade: um item que entrou nos 7 dias antes de
  vencer ou um item que venceu. Cada item é avisado uma vez em cada uma dessas situações.
- Se a validade de um item for alterada, ele pode ser avisado de novo.
- Se o envio falhar, nada é marcado como avisado e a próxima execução tenta de novo.

Configuração, sempre por variáveis de ambiente (no Render) e nunca no código:

| Variável | Para quê |
|---|---|
| `BREVO_API_KEY` | Chave da API da Brevo |
| `EMAIL_REMETENTE` | E-mail remetente verificado na Brevo |
| `EMAIL_REMETENTE_NOME` | Nome que aparece como remetente (opcional; o padrão é "Controle de Despensa") |
| `ALERTAS_TOKEN` | Segredo que o workflow envia; o mesmo valor fica no secret `ALERTAS_TOKEN` do GitHub |
| `URL_PUBLICA` | Endereço usado nos links dos e-mails (opcional; o padrão é o endereço publicado) |

Sem `BREVO_API_KEY` e `EMAIL_REMETENTE`, o app funciona normalmente e só registra no log que o envio de
e-mail está desligado. Sem `ALERTAS_TOKEN`, o endereço de disparo recusa todas as chamadas. Ao iniciar, o
log mostra, para cada uma dessas variáveis, se ela foi encontrada e o tamanho do valor (nunca o valor), e
avisa quando existe uma variável com nome parecido mas diferente.

## Estrutura do repositório

```text
.
├── controle-despensa/   versão desktop (Swing + SQLite) — histórico do TCC I
├── .github/workflows/   disparo diário dos avisos por e-mail
├── despensa-web/        versão web atual (Spring Boot + MongoDB Atlas)
│   ├── src/main/java/   código Java (camadas model, repository, service, web, config)
│   ├── src/main/resources/templates/   páginas Thymeleaf
│   ├── src/main/resources/static/      CSS, JavaScript e ícones
│   ├── src/test/        testes em Java e em JavaScript
│   ├── Dockerfile       imagem usada na publicação
│   └── rodar.cmd        roda a aplicação no computador
└── README.md
```
