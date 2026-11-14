<p align="center">
  <img src="app/src/desktopMain/resources/icon/chirp-rea-128.png" alt="CHIRP-REA" width="128">
</p>

# CHIRP-REA

Programador de memórias de rádios amadores, escrito em **Kotlin + Compose
Multiplatform** a partir do [CHIRP](https://chirpmyradio.com). Usa os mesmos
arquivos `.img` e `.csv` do CHIRP e os mesmos drivers, e acrescenta recursos como
a troca da logo da tela ao ligar.

Validado num **Baofeng UV-21 Pro** real: download, envio e troca de logo.

## Baixar e instalar (Windows)

**[⬇ Baixar a versão mais recente](../../releases/latest)** — na página da release,
em *Assets*, baixe `CHIRP-REA-<versão>.exe` e dê dois cliques.

- Não precisa de Java nem de senha de administrador.
- Cria atalhos na Área de Trabalho e no Menu Iniciar.
- No aviso do SmartScreen, clique em **Mais informações › Executar assim mesmo**
  (o instalador não é assinado digitalmente).

Os instaladores são gerados automaticamente pelo GitHub Actions a cada nova versão
(veja [Publicar uma versão](docs/desenvolvimento.md#publicar-uma-versão)).
Para gerar o seu próprio, siga
[Compilar a partir do código-fonte](docs/instalacao.md#compilar-a-partir-do-código-fonte):

```bash
git clone <url-do-repositorio> chirp-rea
cd chirp-rea
./gradlew :app:packageExe     # Windows: gradlew.bat :app:packageExe
```

## Funcionalidades

### Memórias (canais)
Grade estilo planilha com todos os canais: frequência, nome, tons CTCSS/DCS
(Tone, TSQL, DTCS, Cross com polaridade), duplex (`+`, `-`, split, só recepção),
offset, modo (FM, NFM, AM automático na faixa aérea), potência e pular na
varredura. Edição direta nas células, com validação contra os limites do rádio
(bandas, tamanho e caracteres do nome, códigos aceitos). → [docs/memorias.md](docs/memorias.md)

### Arquivos `.img`, `.csv` e configurações prontas
Abre e salva imagens de rádio no formato do CHIRP (compatíveis nos dois sentidos)
e listas CSV genéricas. Inclui as listas prontas do CHIRP: chamada, FRS/GMRS, MURS,
marítimo, aviação, PMR/LPD, Freenet, UHF CB e outras. → [docs/arquivos.md](docs/arquivos.md)

### Baixar e enviar para o rádio
Comunicação pela serial do cabo USB (CH340, CP210x, Prolific…), com as portas
listadas pelo nome e o cabo sugerido primeiro, barra de progresso e mensagens de
erro claras — incluindo a detecção de **cabo mal encaixado** (eco).
→ [docs/radio-baixar-enviar.md](docs/radio-baixar-enviar.md)

### Configurações do rádio
As opções gerais nos mesmos grupos do CHIRP: **Básico** (squelch, timeout, dual
watch, voz, luz de fundo, bipe, roger, economia de energia, tom piloto, VOX, GPS,
tecla lateral…), **Modo de trabalho** (VFO A/B), **DTMF** (20 códigos PTT-ID, ANI,
velocidades) e **Nomes dos bancos**. Só o que você altera é gravado.
→ [docs/configuracoes.md](docs/configuracoes.md)

### Bancos
10 bancos fixos de 100 canais, com nome editável e lista dos canais de cada
banco. → [docs/bancos.md](docs/bancos.md)

### Logo da tela ao ligar
Troca a imagem que o rádio mostra ao ligar, direto pelo menu **Rádio › Alterar logo
do rádio** (`Alt+L`), sem baixar o rádio antes. Galeria com logos incluídas e as
suas próprias imagens, processamento na hora para o tamanho de cada modelo
(UV-21 Pro: 128×160), três enquadramentos e **pré-visualização exata** antes do
envio. Protocolo obtido por engenharia reversa do utilitário oficial da Baofeng.
→ [docs/logo.md](docs/logo.md)

### Informações e Navegador
Resumo do rádio (driver, bandas, metadados) e uma visão em árvore da memória com
endereços e dump hexadecimal, para usuários avançados.
→ [docs/informacoes-navegador.md](docs/informacoes-navegador.md)

### Diagnóstico de conexão
Sonda somente leitura que identifica o protocolo do rádio (e problemas de cabo)
enviando apenas comandos de identificação. → [docs/diagnostico.md](docs/diagnostico.md)

## Rádios suportados

| Rádio | Situação |
|---|---|
| **Baofeng UV-21 Pro** (original) | download, envio e logo validados em rádio real |
| Baofeng UV-21ProGPS, UV-17ProGPS, UV-17Pro, UV-21 Pro V2 | portados do CHIRP, testados com rádio simulado |

Detalhes e diferenças entre os modelos em [docs/radios-suportados.md](docs/radios-suportados.md).

## Documentação

Toda a documentação de uso está em [`docs/`](docs/README.md):
[instalação](docs/instalacao.md) ·
[primeiros passos](docs/primeiros-passos.md) ·
[memórias](docs/memorias.md) ·
[arquivos](docs/arquivos.md) ·
[baixar e enviar](docs/radio-baixar-enviar.md) ·
[configurações](docs/configuracoes.md) ·
[bancos](docs/bancos.md) ·
[informações e navegador](docs/informacoes-navegador.md) ·
[logo](docs/logo.md) ·
[diagnóstico](docs/diagnostico.md) ·
[rádios suportados](docs/radios-suportados.md) ·
[desenvolvimento](docs/desenvolvimento.md)

## Status do projeto

| Fase | Conteúdo | Estado |
|---|---|---|
| 1 | Núcleo (parser `bitwise`, memória, CSV, `.img`) e app desktop | ✅ |
| 2 | Família Baofeng UV-17Pro / UV-21 Pro: canais, configurações, bancos, logo | ✅ validado em rádio real |
| 2 | Instalador Windows com ícone próprio | ✅ |
| 3 | Demais drivers do CHIRP (~195) | ⏳ |
| 4 | Versão web (Kotlin/Wasm + Web Serial) | ⏳ |

## Para desenvolvedores

```bash
./gradlew :app:run          # rodar
./gradlew build             # compilar e testar (JVM e WebAssembly)
```

Arquitetura, como portar drivers do CHIRP, protocolo da logo e como publicar o
instalador: [docs/desenvolvimento.md](docs/desenvolvimento.md).

## Licença e créditos

Trabalho derivado do CHIRP, de Dan Smith e dos colaboradores do projeto
([kk7ds/chirp](https://github.com/kk7ds/chirp)), distribuído sob a **GNU General
Public License v3.0**. Veja [LICENSE](LICENSE).

- As configurações prontas em `app/src/desktopMain/resources/stock_configs/` vêm do CHIRP.
- O ícone e as logos incluídas são originais deste projeto, desenhados em código
  (`tools/AppIcon.kt` e `tools/LogoArt.kt`), sob a mesma licença.
