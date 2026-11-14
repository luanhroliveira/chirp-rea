# Diagnóstico de conexão (sonda)

Quando o rádio não conecta, a sonda descobre o que está acontecendo **sem gravar
nada no rádio**: ela envia apenas os comandos de identificação usados pelos
drivers da Baofeng e registra os bytes que voltam.

```bash
./gradlew :app:probe -Pport=COM6
```

(Requer o código-fonte; veja [instalação](instalacao.md#compilar-a-partir-do-código-fonte).)

Testa, em sequência:

| Família | Velocidade | Comandos |
|---|---|---|
| UV-17Pro (e variações) | 115200 | `PROGRAMBFNORMALU`, `PROGRAMCOLORPROU`, `PROGRAMBFGMRS05U`, `PROGRAMBF5RTECHU`, `PROGRAMBF-K6PROU` |
| UV-17 | 57600 | `PSEARCH` |
| UV-5R clássico | 9600 | 8 variações de `50 BB FF …` |

O relatório vai para a tela e para `app/build/probe-<porta>.txt`.

## Como ler o resultado

| O que aparece | Significa |
|---|---|
| Todas as respostas iguais ao que foi enviado | **eco**: o conector não está encaixado até o fim |
| Nenhuma resposta em nenhum teste | porta errada, rádio desligado ou cabo sem driver |
| `ACK` em uma linha | esse é o protocolo do rádio; a linha `0x4D` mostra o modelo |

Exemplo real (UV-21 Pro): `PROGRAMCOLORPROU` → `ACK`, `0x4D` → `UV21PRO`,
ou seja, driver **UV-21ProGPS**.
