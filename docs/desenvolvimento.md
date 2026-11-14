# Desenvolvimento

## Estrutura

```
core/   Kotlin Multiplatform puro (JVM + WebAssembly), sem interface
  bitwise/   parser da linguagem MEM_FORMAT dos drivers do CHIRP
  memmap/    imagem binária da memória do rádio
  model/     Memory, tons, DTCS, modos, frequências, potência
  radio/     Radio, CloneModeRadio, RadioFeatures, configurações, bancos, SerialPort, .img
  csv/       formato CSV genérico do CHIRP
  drivers/   drivers portados (baofeng/)
app/    interface Compose
  commonMain/   telas reutilizáveis (grade, configurações, bancos, navegador…)
  desktopMain/  janela, menus, diálogos, serial (jSerialComm), logo, ferramentas
  packaging/    ícone do instalador
docs/   esta documentação
.github/workflows/release.yml  gera e publica o instalador no GitHub Releases
```

O núcleo não usa APIs da JVM, por isso também compila para WebAssembly (base da
futura versão web com Web Serial).

## Comandos

| Comando | Para quê |
|---|---|
| `./gradlew :app:run` | rodar o app |
| `./gradlew build` | compilar e rodar todos os testes (JVM e WebAssembly) |
| `./gradlew :core:jvmTest` | só os testes do núcleo |
| `./gradlew :app:probe -Pport=COM6` | [sonda de diagnóstico](diagnostico.md) |
| `./gradlew :app:generateLogos` | redesenhar as logos incluídas (`tools/LogoArt.kt`) |
| `./gradlew :app:generateIcon` | redesenhar o ícone do app (`tools/AppIcon.kt`) |
| `./gradlew :app:packageExe "-PappVersion=X.Y.Z"` | gerar o instalador `.exe` localmente (aspas necessárias no PowerShell) |

## Portar um driver do CHIRP

Os drivers do CHIRP descrevem a memória numa mini-linguagem (`MEM_FORMAT`). O
parser `bitwise` implementa a mesma linguagem, então o layout é copiado sem mudança:

```kotlin
val obj = Bitwise.parse(MEM_FORMAT, mmap)
val rx = obj["memory"][0]["rxfreq"].value * 10   // lbcd[4] em unidades de 10 Hz
obj["memory"][0]["name"].string = "REPEAT      "
```

1. Crie uma subclasse de `CloneModeRadio` com `memFormat`, `features()`,
   `getMemory`, `setMemory`, `syncIn` e `syncOut`.
2. Registre o `DriverInfo` em `Drivers.registerAll()`; `chirpClass` deve ser o
   nome da classe Python, para os `.img` ficarem compatíveis.
3. Escreva testes com um rádio simulado (veja `BaofengUV17ProTest`).

## Protocolo da logo

Obtido por engenharia reversa (com o REA) do `Import Picture tools.exe` v1.2.4.11:

- serial 115200 8N1, DTR e RTS ligados;
- entrada: `PROGRAMCOLORPROU` → `06` → envia `'D'`;
- pacotes `A5 cmd idHi idLo lenHi lenLo dados… crcHi crcLo`, CRC-16/XMODEM sobre `cmd…dados`;
- sequência: cmd 2 `"PROGRAM"` → cmd 4 apagar (`id 0x4504`, endereço LE + `00 01`)
  → cmd 3 endereço (LE) → cmd 87 dados (1024 bytes por pacote) → cmd 6 `"Over"`;
- cada pacote é respondido com cmd igual e dado `'Y'`;
- imagem em RGB565 little-endian, linha a linha; UV-21 Pro: 128×160 em `0x100000`.

## Publicar uma versão

Os instaladores **não** ficam no repositório: são publicados no **GitHub Releases**
pelo workflow [`.github/workflows/release.yml`](../.github/workflows/release.yml).

```bash
git tag v1.0.1
git push origin v1.0.1
```

O GitHub Actions então, numa máquina Windows:

1. roda todos os testes (`gradlew build`);
2. gera `CHIRP-REA-1.0.1.exe` com a versão tirada da tag;
3. cria a release **v1.0.1** com o `.exe` anexado, instruções de instalação e o SHA-256.

Também dá para disparar pela aba **Actions › Release › Run workflow**, informando
a versão.

Para publicar à mão (sem Actions): gere o `.exe` com
`./gradlew :app:packageExe "-PappVersion=1.0.1"`, crie a release em
**Releases › Draft a new release** e anexe o arquivo de
`app/build/compose/binaries/main/exe/`.

Regras:

- versões no formato `MAJOR.MINOR.PATCH`, com MAJOR > 0 (exigência do instalador);
- nunca altere o `upgradeUuid` em `app/build.gradle.kts`: é ele que permite à nova
  versão substituir a instalada.
