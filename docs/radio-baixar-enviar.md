# Baixar e enviar (rádio)

## Preparar

1. Rádio **desligado**.
2. Encaixe o cabo **até o fim** no rádio. No conector K1 (dois pinos) da Baofeng
   o encaixe é bem justo; um plugue meio solto é a causa mais comum de falha.
3. Conecte o USB no computador e **ligue o rádio** (volume baixo).

## Baixar do rádio (`Alt+D`)

1. **Rádio › Baixar do rádio…**
2. **Porta**: o app lista as portas com o nome do dispositivo e coloca os cabos
   USB primeiro, por exemplo `COM6 — USB-SERIAL CH340`. Portas "Bluetooth" não
   são o cabo.
3. **Modelo**: escolha o seu rádio. Para o UV-21 Pro original, use
   **Baofeng UV-21 Pro (driver UV-21ProGPS)**.
4. **Baixar**. A barra mostra o progresso; ao terminar, abre uma aba com o rádio.
5. **Salve um backup** (`Arquivo › Salvar como…`).

## Enviar para o rádio (`Alt+U`)

1. Deixe aberta a aba do rádio (baixado ou aberto de um `.img`).
2. **Rádio › Enviar para o rádio…**. O modelo vem do próprio arquivo.
3. **Enviar** e não mexa no cabo até terminar.

O envio grava a memória inteira (canais, configurações e nomes dos bancos).
Para desfazer, abra o `.img` de backup e envie de novo.

## Mensagens de erro

| Mensagem | Causa provável | O que fazer |
|---|---|---|
| "O cabo devolveu o próprio comando (eco)…" | plugue não encaixado até o fim | empurre o conector com firmeza e tente de novo |
| "O rádio não respondeu ao handshake" | porta errada, rádio desligado ou modelo errado | confira a porta (cabo USB, não Bluetooth), ligue o rádio, teste outro modelo da família |
| "Resposta inesperada do rádio" | o rádio fala outro protocolo | rode o [diagnóstico](diagnostico.md) |
| "Resposta incompleta do rádio" | cabo com mau contato ou interferência | reconecte e tente de novo |
| "Bad ack writing block…" | o rádio recusou um bloco durante o envio | desligue e ligue o rádio e envie de novo |
| "Não foi possível abrir COMx" | a porta está em uso | feche outros programas (CHIRP, software da Baofeng) |
