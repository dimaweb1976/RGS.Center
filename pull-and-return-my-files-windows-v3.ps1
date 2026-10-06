# pull-and-return-my-files.ps1
# Version 3 notes:
#   - Native Git warnings on stderr (e.g. LF -> CRLF) are not treated as fatal
#     when git itself exits with code 0.
#   - File-list commands keep stderr separate from stdout, so Git warnings are
#     never mistaken for repository file paths.
#
#
# Назначение:
#   безопасно привести текущую ветку к точному состоянию REMOTE/BRANCH,
#   сохранив локально изменённые tracked-файлы и untracked-файлы.
#
# Правило при совпадении:
#   локальная сохранённая версия файла побеждает целиком.
#   Merge/rebase не выполняется, поэтому Git merge-конфликтов быть не должно.
#
# Порядок работы:
#   1. Изменённые tracked-файлы и untracked-файлы копируются во временную
#      резервную папку git-local-backup-* в каталоге $env:TEMP.
#   2. Рабочая копия очищается командами git reset --hard HEAD и git clean -fd.
#      Ignored untracked-файлы git clean -fd не удаляет.
#   3. Выполняется git fetch REMOTE.
#   4. Текущая ветка приводится точно к REMOTE/BRANCH:
#          git reset --hard REMOTE/BRANCH
#   5. Сохранённые локальные файлы копируются обратно поверх версии Git.
#   6. После успешного восстановления временная резервная папка удаляется.
#      При ошибке она сохраняется для ручного восстановления.
#
# Важно:
#   - локально удалённые tracked-файлы НЕ восстанавливаются — для них остаётся
#     версия из REMOTE/BRANCH;
#   - staging после запуска теряется: восстановленные изменения будут unstaged;
#   - локальные коммиты, которых нет в REMOTE/BRANCH, будут сняты с текущей
#     ветки командой reset --hard. Если их нужно сохранить, заранее выполните:
#         git branch backup-before-update
#   - ignored untracked-файлы специально не архивируются. Обычно они остаются
#     нетронутыми, но не используйте этот скрипт как резервное копирование
#     важных ignored-файлов.
#
# Скрипт может лежать где угодно. Важно запускать его, находясь ВНУТРИ
# нужного Git-репозитория.
#
# Пример: скрипт лежит C:\Temp\pull-and-return-my-files.ps1
#
#   cd C:\Projects\RGS.Center
#   powershell.exe -NoProfile -ExecutionPolicy Bypass `
#       -File C:\Temp\pull-and-return-my-files.ps1
#
# Если вы уже находитесь в Windows PowerShell:
#
#   cd C:\Projects\RGS.Center
#   & C:\Temp\pull-and-return-my-files.ps1
#
# Если политика выполнения запрещает запуск .ps1, используйте первый вариант
# с -ExecutionPolicy Bypass.
#
# Параметры:
#   powershell.exe -NoProfile -ExecutionPolicy Bypass `
#       -File C:\Temp\pull-and-return-my-files.ps1 `
#       -Remote origin -Branch main -BackupRetentionDays 3

[CmdletBinding()]
param(
    [string]$Remote = $(if ($env:REMOTE) { $env:REMOTE } else { "origin" }),
    [string]$Branch = $(if ($env:BRANCH) { $env:BRANCH } else { "main" }),
    [string]$BackupRoot = $(if ($env:BACKUP_ROOT) { $env:BACKUP_ROOT } else { $env:TEMP }),
    [int]$BackupRetentionDays = $(
        if ($env:BACKUP_RETENTION_DAYS) {
            [int]$env:BACKUP_RETENTION_DAYS
        } else {
            3
        }
    )
)

$ErrorActionPreference = "Stop"
$BackupPrefix = "git-local-backup-"
$RestoreRequired = $false
$BackupDir = $null
$Repo = $null

function Invoke-Git {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments,
        [switch]$Capture
    )

    # Windows PowerShell 5.1 can promote native stderr output (for example,
    # Git LF/CRLF warnings) to PowerShell error records. With the script-wide
    # ErrorActionPreference=Stop this may abort execution even when git exits 0.
    # Therefore we temporarily use Continue around native git execution and
    # decide success/failure strictly from $LASTEXITCODE.
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"

    try {
        if ($Capture) {
            $output = & git @Arguments 2>&1
            $exitCode = $LASTEXITCODE
        }
        else {
            & git @Arguments
            $exitCode = $LASTEXITCODE
        }
    }
    finally {
        $ErrorActionPreference = $oldPreference
    }

    if ($exitCode -ne 0) {
        if ($Capture) {
            throw "git $($Arguments -join ' ') failed:`n$($output -join [Environment]::NewLine)"
        }

        throw "git $($Arguments -join ' ') failed with exit code $exitCode."
    }

    if ($Capture) {
        # Force plain strings so warning records returned from native stderr
        # cannot propagate as PowerShell ErrorRecord objects.
        return @($output | ForEach-Object { "$_" })
    }
}

function Get-GitPaths {
    param([Parameter(Mandatory)][string[]]$Arguments)

    # IMPORTANT:
    # Do NOT merge Git stderr into stdout here.
    # Commands such as `git diff --name-only` can emit LF/CRLF warnings on
    # stderr. If stderr is merged, such warning text is mistaken for a file path
    # and later [IO.Path]::GetFullPath() fails.
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"

    try {
        $lines = @(& git @Arguments)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $oldPreference
    }

    if ($exitCode -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $exitCode."
    }

    return @($lines | ForEach-Object { "$_" } | Where-Object { $_ -ne "" })
}

function Assert-RepositoryPath {
    param([Parameter(Mandatory)][string]$RelativePath)

    if ([string]::IsNullOrWhiteSpace($RelativePath)) {
        throw "Empty repository path."
    }

    if ([IO.Path]::IsPathRooted($RelativePath)) {
        throw "Expected repository-relative path, got rooted path: $RelativePath"
    }

    $root = [IO.Path]::GetFullPath($Repo).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    $joined = Join-Path $Repo $RelativePath
    $fullPath = [IO.Path]::GetFullPath($joined)

    if (-not $fullPath.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe repository path: $RelativePath"
    }

    return $fullPath
}

function Backup-Files {
    param(
        [string[]]$Paths = @(),
        [Parameter(Mandatory)][string]$DestinationRoot
    )

    foreach ($relativePath in $Paths) {
        $source = Assert-RepositoryPath $relativePath
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            continue
        }

        $destination = Join-Path $DestinationRoot $relativePath
        $destinationParent = Split-Path -Parent $destination
        if ($destinationParent) {
            New-Item -ItemType Directory -Path $destinationParent -Force | Out-Null
        }
        Copy-Item -LiteralPath $source -Destination $destination -Force
    }
}

function Restore-OneFile {
    param(
        [Parameter(Mandatory)][string]$SourceFile,
        [Parameter(Mandatory)][string]$RelativePath
    )

    $destination = Assert-RepositoryPath $RelativePath

    # Если в новой версии Git на месте нашего локального файла появилась
    # директория, удаляем её: по выбранному правилу локальный файл побеждает.
    if (Test-Path -LiteralPath $destination -PathType Container) {
        Remove-Item -LiteralPath $destination -Recurse -Force
    }

    $destinationParent = Split-Path -Parent $destination

    if ($destinationParent) {
        # Создаём цепочку родительских каталогов. Если где-то в новой версии
        # Git вместо нужного каталога находится файл, локальный путь побеждает.
        $repoRoot = [IO.Path]::GetFullPath($Repo).TrimEnd('\', '/')
        $parentFull = [IO.Path]::GetFullPath($destinationParent)

        if (-not $parentFull.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Unsafe destination parent: $destinationParent"
        }

        $relativeParent = $parentFull.Substring($repoRoot.Length).TrimStart('\', '/')
        $current = $repoRoot

        if ($relativeParent) {
            foreach ($part in ($relativeParent -split '[\\/]')) {
                if (-not $part) { continue }
                $current = Join-Path $current $part

                if (Test-Path -LiteralPath $current -PathType Leaf) {
                    Remove-Item -LiteralPath $current -Force
                }

                if (-not (Test-Path -LiteralPath $current -PathType Container)) {
                    New-Item -ItemType Directory -Path $current -Force | Out-Null
                }
            }
        }
    }

    Copy-Item -LiteralPath $SourceFile -Destination $destination -Force
}

function Restore-Files {
    Write-Host ""
    Write-Host "== Restoring local files =="

    foreach ($sourceRootName in @("modified", "untracked")) {
        $sourceRoot = Join-Path $BackupDir $sourceRootName
        if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container)) {
            continue
        }

        $normalizedSourceRoot = [IO.Path]::GetFullPath($sourceRoot).TrimEnd('\', '/')

        Get-ChildItem -LiteralPath $sourceRoot -File -Recurse | ForEach-Object {
            # Совместимо с Windows PowerShell 5.1: GetRelativePath там нет.
            $normalizedSource = [IO.Path]::GetFullPath($_.FullName)
            $relativePath = $normalizedSource.Substring(
                $normalizedSourceRoot.Length
            ).TrimStart('\', '/')

            Restore-OneFile -SourceFile $_.FullName -RelativePath $relativePath
        }
    }

    Write-Host "Modified tracked and untracked files restored."
}

try {
    $repoOutput = @(& git rev-parse --show-toplevel 2>$null)
    if ($LASTEXITCODE -ne 0 -or $repoOutput.Count -eq 0) {
        throw "Run this script inside a Git repository."
    }

    $Repo = [IO.Path]::GetFullPath("$($repoOutput[0])")
    Set-Location -LiteralPath $Repo

    $gitDir = "$(Invoke-Git -Arguments @('rev-parse', '--git-dir') -Capture)"
    $operationMarkers = @("MERGE_HEAD", "CHERRY_PICK_HEAD", "rebase-merge", "rebase-apply")
    foreach ($marker in $operationMarkers) {
        if (Test-Path -LiteralPath (Join-Path $gitDir $marker)) {
            throw "A merge, rebase, or cherry-pick is already in progress."
        }
    }

    $currentBranch = "$(Invoke-Git -Arguments @('branch', '--show-current') -Capture)"
    if ($currentBranch -ne $Branch) {
        throw "Current branch is '$currentBranch'; expected '$Branch'."
    }

    if (-not (Test-Path -LiteralPath $BackupRoot -PathType Container)) {
        New-Item -ItemType Directory -Path $BackupRoot -Force | Out-Null
    }
    $BackupRoot = [IO.Path]::GetFullPath($BackupRoot)

    Write-Host "== Removing expired backup directories =="
    $expiration = (Get-Date).AddDays(-$BackupRetentionDays)
    Get-ChildItem -LiteralPath $BackupRoot -Directory -Filter "$BackupPrefix*" |
        Where-Object { $_.LastWriteTime -lt $expiration } |
        ForEach-Object {
            Write-Host $_.FullName
            Remove-Item -LiteralPath $_.FullName -Recurse -Force
        }

    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $BackupDir = Join-Path $BackupRoot "$BackupPrefix$stamp-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
    $modifiedBackup = Join-Path $BackupDir "modified"
    $untrackedBackup = Join-Path $BackupDir "untracked"
    New-Item -ItemType Directory -Path $modifiedBackup, $untrackedBackup -Force | Out-Null

    $modified = Get-GitPaths @(
        'diff', '--no-renames', '--name-only', '--diff-filter=ACMRTUXB', 'HEAD'
    )
    $deleted = Get-GitPaths @(
        'diff', '--no-renames', '--name-only', '--diff-filter=D', 'HEAD'
    )
    $untracked = Get-GitPaths @(
        'ls-files', '--others', '--exclude-standard'
    )

    Write-Host ""
    Write-Host "== Repository: $Repo =="
    Write-Host "== Branch:     $Branch =="
    Write-Host "== Remote:     $Remote =="
    Write-Host "== Backup:     $BackupDir =="
    Write-Host ""
    Write-Host "Modified tracked files: $($modified.Count)"
    Write-Host "Deleted tracked files:  $($deleted.Count)"
    Write-Host "Untracked files:        $($untracked.Count)"

    Backup-Files -Paths $modified -DestinationRoot $modifiedBackup
    Backup-Files -Paths $untracked -DestinationRoot $untrackedBackup
    $RestoreRequired = $true

    Write-Host ""
    Write-Host "== Cleaning working tree =="
    Invoke-Git -Arguments @('reset', '--hard', 'HEAD')
    if ($untracked.Count -gt 0) {
        Invoke-Git -Arguments @('clean', '-fd')
    }

    Write-Host ""
    Write-Host "== Updating from Git =="
    Invoke-Git -Arguments @('fetch', $Remote)

    $remoteRef = "$Remote/$Branch"
    Invoke-Git -Arguments @('rev-parse', '--verify', $remoteRef) | Out-Null

    $oldHead = "$(Invoke-Git -Arguments @('rev-parse', 'HEAD') -Capture)"
    $newHead = "$(Invoke-Git -Arguments @('rev-parse', $remoteRef) -Capture)"
    Set-Content -LiteralPath (Join-Path $BackupDir 'old-head.txt') -Value $oldHead -Encoding ASCII
    Set-Content -LiteralPath (Join-Path $BackupDir 'remote-head.txt') -Value $newHead -Encoding ASCII

    Write-Host "Resetting current branch to $remoteRef ..."
    Invoke-Git -Arguments @('reset', '--hard', $remoteRef)

    Restore-Files
    $RestoreRequired = $false

    Write-Host ""
    Write-Host "== Result =="
    Invoke-Git -Arguments @('status')

    Remove-Item -LiteralPath $BackupDir -Recurse -Force
    Write-Host ""
    Write-Host "Update completed successfully."
    Write-Host "Current branch now uses $Remote/$Branch as its base."
    Write-Host "Modified tracked and untracked local files were restored on top."
    Write-Host "Locally deleted tracked files remain at the Git version."
    Write-Host "No merge/rebase was performed."
    Write-Host "Temporary backup directory removed."
}
catch {
    $host.UI.WriteErrorLine("ERROR: $($_.Exception.Message)")

    if ($RestoreRequired -and $BackupDir -and (Test-Path -LiteralPath $BackupDir)) {
        try {
            Restore-Files
        }
        catch {
            Write-Warning "Automatic restore failed: $_"
        }
    }

    if ($BackupDir -and (Test-Path -LiteralPath $BackupDir)) {
        Write-Host "Backup retained at: $BackupDir"
    }

    if ($Repo) {
        & git status
    }
    exit 1
}
