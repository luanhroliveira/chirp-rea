# Memórias (canais)

A sub-aba **Memórias** mostra todos os canais do rádio numa grade, como uma planilha.

## Colunas

| Coluna | Significado |
|---|---|
| **Loc** | número do canal (UV-21 Pro: 1 a 1000) |
| **Frequency** | frequência de recepção em MHz (`146.520000`) |
| **Name** | nome exibido no rádio (UV-21 Pro: até 12 caracteres, sem acentos) |
| **Tone Mode** | `""` nenhum, `Tone` (CTCSS só na TX), `TSQL` (CTCSS TX e RX), `DTCS` (DCS), `Cross` |
| **Tone** | tom CTCSS de transmissão (modo Tone) |
| **ToneSql** | tom CTCSS de squelch (modo TSQL) |
| **DTCS Code** | código DCS |
| **RX DTCS** | código DCS de recepção (modo Cross) |
| **DTCS Pol** | polaridade DCS TX/RX: `NN`, `NR`, `RN`, `RR` |
| **Cross Mode** | combinação TX→RX no modo Cross (ex.: `Tone->DTCS`) |
| **Duplex** | `""` simplex, `+`/`-` repetidora, `split` (TX em outra frequência), `off` (só recepção) |
| **Offset** | deslocamento da repetidora em MHz, ou a frequência de TX quando o duplex é `split` |
| **Mode** | `FM`, `NFM` (banda estreita) ou `AM` (faixa aérea 108–136 MHz, definido automaticamente) |
| **Power** | potência (UV-21 Pro: High 5 W, Low 1 W) |
| **Skip** | `S` = pular o canal na varredura |
| **Comment** | comentário (só em arquivos CSV) |

As colunas que o rádio não suporta não aparecem.

## Editar

- **Texto** (frequência, nome, offset, comentário): clique, digite e tecle **Enter**
  (ou clique fora da célula).
- **Listas** (tom, modo, potência…): clique na célula e escolha a opção.
- **Canal vazio**: digite a frequência para criar o canal; as outras colunas
  ganham valores padrão.
- **Apagar canal**: botão **✕** no fim da linha.

Cada alteração é validada contra o que o rádio aceita (bandas, tamanho do nome,
caracteres, tons, DCS). Se algo for inválido, a alteração é recusada e o motivo
aparece na barra de status.

## Duplex e offset, na prática

| Situação | Duplex | Offset |
|---|---|---|
| Simplex | (vazio) | — |
| Repetidora 2 m (+600 kHz) | `+` | `0.600000` |
| Repetidora 70 cm (−5 MHz) | `-` | `5.000000` |
| TX em outra banda | `split` | frequência de TX, ex. `435.000000` |
| Só escuta | `off` | — |
