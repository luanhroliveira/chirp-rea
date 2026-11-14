# Versão web

**Acesse: https://luanhroliveira.github.io/chirp-rea/**

É o mesmo CHIRP-REA, compilado para WebAssembly, rodando direto no navegador —
nada para instalar. Os arquivos e a comunicação com o rádio ficam no seu
computador: nada é enviado para servidores.

## Navegadores

| Navegador | Abrir/editar/salvar arquivos | Conversar com o rádio |
|---|---|---|
| Google Chrome (computador) | sim | sim |
| Microsoft Edge (computador) | sim | sim |
| Opera, Brave e outros baseados no Chromium | sim | geralmente sim |
| Firefox, Safari | sim | não (sem Web Serial) |
| Celular | sim | não |

A comunicação com o rádio usa a **Web Serial API**, que só existe nos navegadores
baseados no Chromium para computador.

## O que dá para fazer

- **Arquivo › Novo / Abrir .csv ou .img / Abrir configuração pronta**
- **Arquivo › Salvar (download)**: o navegador baixa o `.img` ou `.csv` para a
  pasta de downloads.
- **Rádio › Alterar logo do rádio** (ou **Alterar logo…** em Configurações ›
  Tela ao ligar): galeria, pré-visualização e envio, como no app para Windows
  ([detalhes](logo.md)).
- **Rádio › Baixar do rádio / Enviar para o rádio**: ao clicar em
  *Escolher porta…*, o navegador mostra a lista de portas — escolha a do cabo
  (ex.: *USB-SERIAL CH340*) e clique em **Conectar**.
- Abas **Memórias, Configurações, Bancos, Informações e Navegador**, iguais às
  do app para Windows ([memórias](memorias.md), [configurações](configuracoes.md),
  [bancos](bancos.md), [informações e navegador](informacoes-navegador.md)).

## Diferenças em relação ao app para Windows

| Recurso | Windows | Web |
|---|---|---|
| Logo da tela ao ligar | sim | sim (galeria com as mesmas 33 imagens; sem "Minhas imagens") |
| Salvar no mesmo arquivo | sim (`Ctrl+S`) | sempre como download |
| Atalhos de teclado do menu | sim | não |
| Precisa instalar | sim | não |

## Problemas comuns

| Situação | O que fazer |
|---|---|
| Aviso "Este navegador não tem Web Serial" | use o Chrome ou o Edge no computador |
| A porta do cabo não aparece na lista | instale o driver do cabo (CH340/CP210x) e reconecte; feche outros programas que usem a porta |
| "A porta já está em uso" | feche o CHIRP, o software da Baofeng ou o app CHIRP-REA para Windows |
| Eco / sem resposta | veja [Baixar e enviar](radio-baixar-enviar.md#mensagens-de-erro) |

## Para desenvolvedores

```bash
./gradlew :app:wasmJsBrowserDistribution
```

O site fica em `app/build/dist/wasmJs/productionExecutable/` (sirva com qualquer
servidor HTTP; abrir o `index.html` direto do disco não funciona). O workflow
[`.github/workflows/pages.yml`](../.github/workflows/pages.yml) publica no GitHub
Pages a cada push na `main`.
