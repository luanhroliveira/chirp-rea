# Arquivos: CSV, IMG e configurações prontas

## Imagem de rádio (`.img`)

É a cópia completa da memória do rádio, no **mesmo formato do CHIRP**: arquivos
`.img` gravados pelo CHIRP abrem no CHIRP-REA e vice-versa.

- Contém canais, configurações, bancos e metadados (modelo, driver).
- Ao abrir um `.img`, o app escolhe o driver pelo modelo gravado no arquivo.
  Se o driver ainda não foi portado, aparece o erro
  "O driver 'X' ainda não foi portado para o CHIRP-REA".
- **Sempre** salve um `.img` logo após o primeiro download: é o seu backup para
  restaurar o rádio.

## Lista de canais (`.csv`)

Formato genérico do CHIRP, independente de modelo. Bom para montar listas,
editar em planilhas e copiar canais entre rádios.

Colunas: `Location, Name, Frequency, Duplex, Offset, Tone, rToneFreq, cToneFreq,
DtcsCode, DtcsPolarity, RxDtcsCode, CrossMode, Mode, TStep, Skip, Power, Comment`
(e as colunas D-STAR `URCALL`, `RPT1CALL`, `RPT2CALL`, `DVCODE`, mantidas vazias).

- Linhas com problemas são ignoradas e contadas como aviso na barra de status.
- Se o arquivo tiver só `rToneFreq` ou só `cToneFreq`, o valor vale para os dois
  (como no CHIRP).

## Configurações prontas

**Arquivo › Abrir configuração pronta** traz as listas que acompanham o CHIRP:
frequências de chamada (EUA, Canadá, Polônia), FRS/GMRS, MURS, marítimo VHF
(EUA, França, Grécia), aviação, PMR/LPD (Europa), Freenet (Alemanha),
UHF CB (Austrália/Nova Zelândia), canais de 60 m, ferrovias, NOAA e outras.

Elas abrem como CSV; salve com outro nome para editar.

## Salvar

- **Arquivo › Salvar** (`Ctrl+S`) grava no mesmo arquivo.
- **Arquivo › Salvar como…** escolhe outro nome. Rádios são salvos como `.img`,
  listas como `.csv`.
