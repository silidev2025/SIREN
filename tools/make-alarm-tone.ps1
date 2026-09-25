param(
    [string]$OutPath = "C:\Users\Administrator\Desktop\Project\app\src\main\res\raw\siren_alarm.wav"
)

$ErrorActionPreference = 'Stop'

$sampleRate = 44100
$amplitude  = 0.82
$segments   = @(960.0, 720.0, 960.0, 720.0)
$segSeconds = 0.45

$samples = New-Object System.Collections.Generic.List[int16]

foreach ($freq in $segments) {

    $cycles = [math]::Round($freq * $segSeconds)
    $count  = [int][math]::Round($cycles * $sampleRate / $freq)

    for ($i = 0; $i -lt $count; $i++) {
        $phase = 2.0 * [math]::PI * $freq * $i / $sampleRate
        $sine  = [math]::Sin($phase)

        $square = if ($sine -ge 0) { 1.0 } else { -1.0 }
        $value = (0.72 * $square) + (0.28 * $sine)

        $ramp = 1.0
        $rampLen = [int]($sampleRate * 0.004)
        if ($i -lt $rampLen) { $ramp = $i / $rampLen }
        elseif ($i -ge ($count - $rampLen)) { $ramp = ($count - $i) / $rampLen }

        $s = [int][math]::Round($value * $ramp * $amplitude * 32767)
        if ($s -gt 32767) { $s = 32767 }
        if ($s -lt -32768) { $s = -32768 }
        $samples.Add([int16]$s)
    }
}

$dataBytes = $samples.Count * 2
$dir = Split-Path $OutPath -Parent
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

$fs = [System.IO.File]::Create($OutPath)
$bw = New-Object System.IO.BinaryWriter($fs)
try {

    $bw.Write([char[]]'RIFF')
    $bw.Write([int](36 + $dataBytes))
    $bw.Write([char[]]'WAVE')

    $bw.Write([char[]]'fmt ')
    $bw.Write([int]16)
    $bw.Write([int16]1)
    $bw.Write([int16]1)
    $bw.Write([int]$sampleRate)
    $bw.Write([int]($sampleRate * 2))
    $bw.Write([int16]2)
    $bw.Write([int16]16)

    $bw.Write([char[]]'data')
    $bw.Write([int]$dataBytes)
    foreach ($s in $samples) { $bw.Write($s) }
} finally {
    $bw.Dispose()
    $fs.Dispose()
}

$len = $samples.Count / [double]$sampleRate
Write-Output ("wrote {0}" -f $OutPath)
Write-Output ("  samples : {0}" -f $samples.Count)
Write-Output ("  duration: {0:N3}s (loops seamlessly)" -f $len)
Write-Output ("  size    : {0:N0} bytes" -f (Get-Item $OutPath).Length)
