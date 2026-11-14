# Instalação

## Windows — instalador com um clique

1. Abra a página de **[Releases](../../../releases/latest)** do repositório no GitHub
   (link "Releases", à direita na página inicial do repositório).
2. Na versão mais recente, em **Assets**, baixe `CHIRP-REA-<versão>.exe`
   e dê dois cliques no arquivo baixado.
3. Siga o assistente: escolha a pasta (ou aceite a padrão) e conclua.

O instalador:

- **não pede senha de administrador**: instala só para o seu usuário, em
  `%LOCALAPPDATA%\CHIRP-REA`;
- cria um atalho na **Área de Trabalho** e no **Menu Iniciar** (grupo CHIRP-REA);
- já traz o Java embutido — **não é preciso instalar Java**;
- atualiza a versão anterior quando você instala uma nova.

### Aviso do Windows SmartScreen

O instalador não é assinado digitalmente (um certificado de assinatura é pago).
Na primeira execução o Windows pode mostrar **"O Windows protegeu o computador"**.
Clique em **Mais informações › Executar assim mesmo**.

Para conferir que o arquivo não foi alterado, compare o SHA-256 publicado nas
notas da release com o do arquivo baixado:

```powershell
Get-FileHash .\CHIRP-REA-<versão>.exe
```

### Desinstalar

**Configurações do Windows › Aplicativos › CHIRP-REA › Desinstalar.**
Suas imagens em `%USERPROFILE%\.chirp-rea\logos` não são apagadas.

## Cabo de programação

O app usa a porta serial criada pelo cabo USB (normalmente **CH340**, **CP210x**
ou **Prolific**). Se a porta não aparecer no app, instale o driver do cabo
fornecido pelo fabricante e reconecte. Veja [Baixar e enviar](radio-baixar-enviar.md).

## Compilar a partir do código-fonte

Use este caminho se ainda não houver uma release publicada, se quiser testar uma
alteração ou gerar a sua própria versão.

Requisitos: **JDK 21** (por exemplo, [Eclipse Temurin 21](https://adoptium.net))
e **Git**. Todo o resto o Gradle baixa sozinho.

```bash
git clone <url-do-repositorio> chirp-rea
cd chirp-rea
./gradlew :app:run                      # abre o app direto, sem instalar
./gradlew :app:packageExe               # gera o instalador
```

No Windows, use `gradlew.bat` no lugar de `./gradlew`.

O instalador fica em `app/build/compose/binaries/main/exe/CHIRP-REA-<versão>.exe`.
A primeira geração baixa o WiX Toolset (usado pelo `jpackage`) e leva alguns minutos.

Outros formatos:

| Comando | Resultado |
|---|---|
| `./gradlew :app:packageMsi` | instalador `.msi` (Windows) |
| `./gradlew :app:createDistributable` | pasta com o app pronto para rodar, sem instalador |
| `./gradlew :app:packageDmg` | instalador para macOS (rodar num Mac) |
| `./gradlew :app:packageDeb` | pacote para Debian/Ubuntu (rodar no Linux) |
| `./gradlew :app:packageExe "-PappVersion=1.2.0"` | instalador com outro número de versão (as aspas são necessárias no PowerShell) |

O `jpackage` só gera instaladores para o sistema em que está rodando.
