<#
  Gera as falas da narração (roteiro.txt) com as vozes OneCore do Windows (pt-BR)
  e remove os silêncios das pontas com o FFmpeg.

  Uso:
    powershell -ExecutionPolicy Bypass -File app\promo\narration\tts.ps1 `
      -OutDir app\build\promo\narration [-Voice Daniel] [-Rate 1.12] [-FFmpeg ffmpeg]
#>
param(
    [Parameter(Mandatory = $true)][string]$OutDir,
    [string]$Voice = "Daniel",
    [double]$Rate = 1.12,
    [string]$FFmpeg = "ffmpeg"
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.SpeechSynthesis.SpeechSynthesizer, Windows.Media, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]

$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, [Type]$t) { $task = $asTask.MakeGenericMethod($t).Invoke($null, @($op)); $task.Wait(-1) | Out-Null; $task.Result }

$synth = New-Object Windows.Media.SpeechSynthesis.SpeechSynthesizer
$v = [Windows.Media.SpeechSynthesis.SpeechSynthesizer]::AllVoices |
    Where-Object { $_.Language -eq 'pt-BR' -and $_.DisplayName -like "*$Voice*" } | Select-Object -First 1
if (-not $v) { throw "Voz '$Voice' (pt-BR) não encontrada. Instale o pacote de fala em Português (Brasil) do Windows." }
$synth.Voice = $v
$synth.Options.SpeakingRate = $Rate
Write-Host "voz: $($v.DisplayName), velocidade $Rate"

$raw = Join-Path $OutDir "raw"
New-Item -ItemType Directory -Force $raw | Out-Null
$lines = Get-Content (Join-Path $PSScriptRoot "roteiro.txt") -Encoding UTF8 | Where-Object { $_ -match '^\s*\d+\s*\|' }
foreach ($line in $lines) {
    $id, $text = $line -split '\|', 2
    $id = $id.Trim()
    $stream = Await ($synth.SynthesizeTextToStreamAsync($text.Trim())) ([Windows.Media.SpeechSynthesis.SpeechSynthesisStream])
    $reader = New-Object Windows.Storage.Streams.DataReader($stream.GetInputStreamAt(0))
    $size = [uint32]$stream.Size
    $null = Await ($reader.LoadAsync($size)) ([uint32])
    $bytes = New-Object byte[] $size
    $reader.ReadBytes($bytes)
    $rawFile = Join-Path $raw "$id.wav"
    [IO.File]::WriteAllBytes($rawFile, $bytes)
    & $FFmpeg -v error -y -i $rawFile `
        -af "silenceremove=start_periods=1:start_threshold=-45dB,areverse,silenceremove=start_periods=1:start_threshold=-45dB,areverse" `
        -ar 48000 -ac 1 (Join-Path $OutDir "$id.wav")
    Write-Host "fala $id pronta"
}
