# Rádios suportados

| Rádio | Driver (classe CHIRP) | Situação |
|---|---|---|
| **Baofeng UV-21 Pro** (original, firmware "COLOR PRO") | `UV21ProGPS` | download, envio e logo **validados num rádio real** |
| Baofeng UV-21ProGPS | `UV21ProGPS` | mesmo protocolo do UV-21 Pro |
| Baofeng UV-17ProGPS | `UV17ProGPS` | portado do CHIRP; testado com rádio simulado |
| Baofeng UV-17Pro | `UV17Pro` | portado do CHIRP; testado com rádio simulado |
| Baofeng UV-21 Pro V2 | `UV21ProV2` | portado do CHIRP; testado com rádio simulado |
| Baofeng UV-21R | apelido de `UV17Pro` | relato da comunidade ([CHIRP #10581](https://chirpmyradio.com/issues/10581)) |
| Genérico CSV | — | listas de canais em arquivo |

**UV-21 Pro × UV-21 Pro V2:** são rádios diferentes. O UV-21 Pro original responde
ao comando `PROGRAMCOLORPROU` e se identifica como `UV21PRO`; o V2 usa
`PROGRAMBFNORMALU`. Se não souber qual é o seu, use o [diagnóstico](diagnostico.md).

## Recursos por modelo

| Recurso | UV-17Pro / UV-21 Pro V2 | UV-17ProGPS / UV-21 Pro |
|---|---|---|
| Canais | 1000 | 1000 |
| Configurações | sim | sim (+ GPS, VOX, tom piloto, tecla lateral) |
| Bancos (10 × 100, nome editável) | — | sim |
| Logo da tela ao ligar | — | sim |
| Faixa aérea (AM) | V2: sim | sim |

Os demais ~195 drivers do CHIRP ainda serão portados. Arquivos `.img` de rádios
sem driver não abrem.
