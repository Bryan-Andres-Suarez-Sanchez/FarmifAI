param(
    [ValidateSet("debug", "release")]
    [string]$BuildType = "debug",
    [string]$Remote = "origin",
    [string]$Branch = "apk-builds",
    [string]$ApkPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$tempWorktree = $null
Push-Location $repoRoot

try {
    $apk = $null
    if (-not [string]::IsNullOrWhiteSpace($ApkPath)) {
        if (-not (Test-Path $ApkPath -PathType Leaf)) {
            throw "No existe APK en la ruta indicada: $ApkPath"
        }
        $apk = Get-Item (Resolve-Path $ApkPath)
        Write-Host "Usando APK existente: $($apk.FullName)" -ForegroundColor Cyan
    } else {
        $gradleWrapper = Join-Path $repoRoot "gradlew.bat"
        if (-not (Test-Path $gradleWrapper)) {
            throw "No se encontro gradlew.bat en: $repoRoot"
        }

        if ($BuildType -eq "release") {
            $task = ":app:assembleRelease"
            $apkDir = Join-Path $repoRoot "app\build\outputs\apk\release"
        } else {
            $task = ":app:assembleDebug"
            $apkDir = Join-Path $repoRoot "app\build\outputs\apk\debug"
        }

        Write-Host "Compilando APK ($BuildType)..." -ForegroundColor Cyan
        & $gradleWrapper $task --no-daemon | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Fallo la compilacion Gradle."
        }

        if (-not (Test-Path $apkDir)) {
            throw "No existe la carpeta de salida APK: $apkDir"
        }

        $apk = Get-ChildItem -Path $apkDir -Filter *.apk -File |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if (-not $apk) {
            throw "No se encontro APK en: $apkDir"
        }
    }

    $shortSha = (git rev-parse --short HEAD).Trim()
    $sourceBranch = (git rev-parse --abbrev-ref HEAD).Trim()
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $baseName = "FarmifAI-$BuildType-$timestamp-$shortSha.apk"

    $repoUrl = (git remote get-url $Remote).Trim()
    if (-not $repoUrl) {
        throw "No se pudo leer la URL del remoto '$Remote'."
    }

    $tempWorktree = Join-Path $env:TEMP ("farmifai-apk-publish-" + [Guid]::NewGuid().ToString("N"))

    $null = git show-ref --verify --quiet "refs/heads/$Branch"
    $localBranchExists = ($LASTEXITCODE -eq 0)

    $null = git ls-remote --exit-code --heads $Remote $Branch 2>$null
    $remoteBranchExists = ($LASTEXITCODE -eq 0)

    if ($localBranchExists) {
        git worktree add $tempWorktree $Branch | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "No se pudo crear worktree para la rama local '$Branch'."
        }
    } elseif ($remoteBranchExists) {
        git worktree add -b $Branch $tempWorktree "$Remote/$Branch" | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "No se pudo crear worktree desde '$Remote/$Branch'."
        }
    } else {
        git worktree add --detach $tempWorktree HEAD | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "No se pudo crear worktree temporal."
        }
        Push-Location $tempWorktree
        try {
            git checkout --orphan $Branch | Out-Host
            if ($LASTEXITCODE -ne 0) {
                throw "No se pudo crear la rama huerfana '$Branch'."
            }
            git rm -rf . *> $null
            Get-ChildItem -Force | ForEach-Object {
                if ($_.Name -ne ".git") {
                    Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
                }
            }
        } finally {
            Pop-Location
        }
    }

    Push-Location $tempWorktree
    try {
        $artifactDir = Join-Path $tempWorktree "apk-artifacts\$BuildType"
        New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null

        $versionedApk = Join-Path $artifactDir $baseName
        $latestApk = Join-Path $artifactDir "latest-$BuildType.apk"
        Copy-Item $apk.FullName $versionedApk -Force
        Copy-Item $apk.FullName $latestApk -Force

        $metaFile = Join-Path $artifactDir "latest-$BuildType.json"
        $meta = [ordered]@{
            file = (Split-Path $latestApk -Leaf)
            archived_file = (Split-Path $versionedApk -Leaf)
            source_commit = $shortSha
            source_branch = $sourceBranch
            built_at_utc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        } | ConvertTo-Json -Depth 3
        Set-Content -Path $metaFile -Value $meta -Encoding UTF8

        git add "apk-artifacts/$BuildType" | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "No se pudo agregar apk-artifacts al indice git."
        }
        git diff --cached --quiet
        if ($LASTEXITCODE -eq 0) {
            Write-Host "No hay cambios para publicar." -ForegroundColor Yellow
        } else {
            $currentUserName = (git config user.name 2>$null)
            if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($currentUserName)) {
                git config user.name "FarmifAI APK Bot" | Out-Host
                if ($LASTEXITCODE -ne 0) {
                    throw "No se pudo configurar user.name para commit."
                }
            }
            $currentUserEmail = (git config user.email 2>$null)
            if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($currentUserEmail)) {
                git config user.email "farmifai-apk-bot@local" | Out-Host
                if ($LASTEXITCODE -ne 0) {
                    throw "No se pudo configurar user.email para commit."
                }
            }

            $commitMsg = "apk($BuildType): $baseName"
            git commit -m $commitMsg | Out-Host
            if ($LASTEXITCODE -ne 0) {
                throw "Fallo el commit de artefactos APK."
            }
            git push $Remote "HEAD:$Branch" | Out-Host
            if ($LASTEXITCODE -ne 0) {
                throw "Fallo el push hacia '$Remote/$Branch'."
            }
        }
    } finally {
        Pop-Location
    }

    $normalizedRepoUrl = $repoUrl
    if ($normalizedRepoUrl -match "^git@github\.com:(.+)\.git$") {
        $normalizedRepoUrl = "https://github.com/$($Matches[1])"
    } elseif ($normalizedRepoUrl -match "^https://github\.com/(.+)\.git$") {
        $normalizedRepoUrl = "https://github.com/$($Matches[1])"
    }

    $repoPath = $normalizedRepoUrl -replace "^https://github.com/", ""
    $downloadLink = "https://raw.githubusercontent.com/$repoPath/$Branch/apk-artifacts/$BuildType/latest-$BuildType.apk"
    $browseLink = "$normalizedRepoUrl/tree/$Branch/apk-artifacts/$BuildType"

    Write-Host ""
    Write-Host "APK publicada correctamente." -ForegroundColor Green
    Write-Host "Archivo local: $($apk.FullName)"
    Write-Host "Repositorio: $normalizedRepoUrl"
    Write-Host "Carpeta publicada: $browseLink"
    Write-Host "Descarga directa: $downloadLink"
}
finally {
    Pop-Location
    if ($tempWorktree -and (Test-Path $tempWorktree)) {
        $gitWorktreePath = $tempWorktree -replace "\\", "/"
        git worktree remove "$gitWorktreePath" --force 2>$null
        if ($LASTEXITCODE -ne 0) {
            Remove-Item -LiteralPath $tempWorktree -Recurse -Force -ErrorAction SilentlyContinue
            git worktree prune 2>$null | Out-Null
        }
    }
}
