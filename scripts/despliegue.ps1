param(
    [ValidateSet("debug", "release")]
    [string]$BuildType = "debug",
    [string]$Device = "192.168.101.75:46241",
    [string]$Remote = "origin",
    [string]$Branch = "apk-builds",
    [string]$PackageName = "edu.unicauca.app.agrochat",
    [string]$PairTarget = "",
    [string]$PairCode = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "No se encontro el comando requerido: $Name"
    }
}

function Invoke-External {
    param(
        [string]$FilePath,
        [string]$Arguments = "",
        [switch]$AllowFailure
    )

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $FilePath
    $psi.Arguments = $Arguments
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    [void]$process.Start()

    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    $combined = "$stdout`n$stderr".Trim()
    if (-not [string]::IsNullOrWhiteSpace($combined)) {
        $combined -split "(`r`n|`n|`r)" | ForEach-Object {
            if (-not [string]::IsNullOrWhiteSpace($_)) {
                Write-Host $_
            }
        }
    }

    if (-not $AllowFailure -and $process.ExitCode -ne 0) {
        throw "Fallo comando: $FilePath $Arguments (exit=$($process.ExitCode))"
    }

    return [PSCustomObject]@{
        ExitCode = $process.ExitCode
        Output = $combined
    }
}

function Test-AdbDeviceReady {
    param([string]$Serial)
    $result = Invoke-External -FilePath "adb" -Arguments "-s `"$Serial`" get-state" -AllowFailure
    return ($result.ExitCode -eq 0 -and $result.Output -match "device")
}

$scriptRoot = (Resolve-Path $PSScriptRoot).Path
$repoRoot = $null

if (Test-Path (Join-Path $scriptRoot "gradlew.bat") -PathType Leaf) {
    $repoRoot = $scriptRoot
} elseif (Test-Path (Join-Path $scriptRoot "..\gradlew.bat") -PathType Leaf) {
    $repoRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
} else {
    throw "No se pudo determinar la raiz del repositorio desde: $scriptRoot"
}

$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$publishScript = @(
    Join-Path $scriptRoot "build_and_publish_apk.ps1"
    Join-Path $repoRoot "scripts\build_and_publish_apk.ps1"
) | Where-Object { Test-Path $_ -PathType Leaf } | Select-Object -First 1

Require-Command "git"
Require-Command "adb"

if (-not (Test-Path $gradleWrapper -PathType Leaf)) {
    throw "No se encontro gradlew.bat en: $repoRoot"
}
if (-not $publishScript) {
    throw "No se encontro el script de publicacion: $publishScript"
}

Push-Location $repoRoot
try {
    if ($BuildType -eq "release") {
        $task = ":app:assembleRelease"
        $apkDir = Join-Path $repoRoot "app\build\outputs\apk\release"
    } else {
        $task = ":app:assembleDebug"
        $apkDir = Join-Path $repoRoot "app\build\outputs\apk\debug"
    }

    Write-Step "Compilando APK ($BuildType) con los cambios actuales"
    & $gradleWrapper $task --no-daemon | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Fallo la compilacion de la APK."
    }

    $apk = Get-ChildItem -Path $apkDir -Filter *.apk -File |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $apk) {
        throw "No se encontro APK en: $apkDir"
    }
    Write-Host "APK generada: $($apk.FullName)" -ForegroundColor Green

    Write-Step "Publicando APK al repo de despliegue continuo en GitHub"
    & $publishScript -BuildType $BuildType -Remote $Remote -Branch $Branch -ApkPath $apk.FullName | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Fallo la publicacion de la APK."
    }

    $repoUrl = (git remote get-url $Remote).Trim()
    $normalizedRepoUrl = $repoUrl
    if ($normalizedRepoUrl -match "^git@github\.com:(.+)\.git$") {
        $normalizedRepoUrl = "https://github.com/$($Matches[1])"
    } elseif ($normalizedRepoUrl -match "^https://github\.com/(.+)\.git$") {
        $normalizedRepoUrl = "https://github.com/$($Matches[1])"
    }
    $repoPath = $normalizedRepoUrl -replace "^https://github.com/", ""
    $browseLink = "$normalizedRepoUrl/tree/$Branch/apk-artifacts/$BuildType"
    $downloadLink = "https://raw.githubusercontent.com/$repoPath/$Branch/apk-artifacts/$BuildType/latest-$BuildType.apk"

    Write-Step "Conectando por ADB inalambrico al dispositivo $Device"
    Invoke-External -FilePath "adb" -Arguments "start-server" | Out-Null

    if (-not [string]::IsNullOrWhiteSpace($PairTarget) -and -not [string]::IsNullOrWhiteSpace($PairCode)) {
        Write-Step "Emparejando ADB en $PairTarget"
        Invoke-External -FilePath "adb" -Arguments "pair $PairTarget $PairCode" -AllowFailure | Out-Null
    }

    Invoke-External -FilePath "adb" -Arguments "connect $Device" -AllowFailure | Out-Null

    if (-not (Test-AdbDeviceReady -Serial $Device)) {
        $targetIp = ($Device -split ":")[0]
        $targetPort = [int](($Device -split ":")[1])
        $net = Test-NetConnection $targetIp -Port $targetPort -WarningAction SilentlyContinue
        throw "No fue posible conectar por ADB al dispositivo $Device. TcpTestSucceeded=$($net.TcpTestSucceeded), PingSucceeded=$($net.PingSucceeded). Verifica depuracion inalambrica y que el endpoint siga vigente."
    }

    Write-Step "Desinstalando version anterior ($PackageName) en el dispositivo"
    $pkgQuery = Invoke-External -FilePath "adb" -Arguments "-s `"$Device`" shell pm list packages $PackageName"

    if ($pkgQuery.Output -match "package:$([regex]::Escape($PackageName))") {
        $uninstallOutput = Invoke-External -FilePath "adb" -Arguments "-s `"$Device`" uninstall $PackageName" -AllowFailure
        if ($uninstallOutput.Output -notmatch "Success") {
            throw "La desinstalacion de $PackageName no fue exitosa."
        }
    } else {
        Write-Host "No habia una instalacion previa de $PackageName." -ForegroundColor Yellow
    }

    Write-Step "Instalando nueva APK por ADB inalambrico"
    $installOutput = Invoke-External -FilePath "adb" -Arguments "-s `"$Device`" install -r `"$($apk.FullName)`"" -AllowFailure
    if ($installOutput.Output -notmatch "Success") {
        throw "La instalacion de la APK fallo."
    }

    Write-Host ""
    Write-Host "Despliegue completado correctamente." -ForegroundColor Green
    Write-Host "APK local: $($apk.FullName)"
    Write-Host "Carpeta GitHub: $browseLink"
    Write-Host "Link descarga APK: $downloadLink"
}
finally {
    Pop-Location
}
