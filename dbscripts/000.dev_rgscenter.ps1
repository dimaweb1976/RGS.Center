[CmdletBinding()]
param(
    [string]$PsqlPath = 'psql',
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env')
)

$ErrorActionPreference = 'Stop'
$sqlFile = Join-Path $PSScriptRoot '000.rgscenter.tpl'

foreach ($requiredFile in @($EnvFile, $sqlFile)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required file not found: $requiredFile"
    }
}

# Load deployment settings from the project .env file.
$settings = @{}
foreach ($line in Get-Content -LiteralPath $EnvFile) {
    if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') {
        $settings[$Matches[1]] = $Matches[2].Trim('"', "'")
    }
}

$requiredVariables = @(
    'RGSCENTER_POSTGRES_DB_HOST',
    'RGSCENTER_POSTGRES_DB_PORT',
    'RGSCENTER_POSTGRES_DB_NAME',
    'RGSCENTER_POSTGRES_DB_ROOTUSER',
    'RGSCENTER_POSTGRES_DB_ROOTUSER_PASSWORD',
    'RGSCENTER_DBNAME',
    'RGSCENTER_DEDICATED_USER_NAME',
    'RGSCENTER_DEDICATED_USER_PASSWORD',
    'RGSCENTER_DEDICATED_APP_USER_NAME',
    'RGSCENTER_DEDICATED_APP_USER_PASSWORD'
)

foreach ($variableName in $requiredVariables) {
    if ([string]::IsNullOrWhiteSpace($settings[$variableName])) {
        throw "$variableName is not set in $EnvFile"
    }
}

if (-not (Get-Command $PsqlPath -ErrorAction SilentlyContinue)) {
    $psqlCommand = Get-ChildItem -Path 'C:\Program Files\PostgreSQL' `
        -Filter 'psql.exe' -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1

    if ($null -eq $psqlCommand) {
        throw "psql not found: $PsqlPath"
    }
    $PsqlPath = $psqlCommand.FullName
}

$psqlArguments = @(
    '-v', 'ON_ERROR_STOP=1',
    '-v', "dbname=$($settings.RGSCENTER_DBNAME)",
    '-v', "dedicated_user_name=$($settings.RGSCENTER_DEDICATED_USER_NAME)",
    '-v', "dedicated_user_password=$($settings.RGSCENTER_DEDICATED_USER_PASSWORD)",
    '-v', "dedicated_app_user_name=$($settings.RGSCENTER_DEDICATED_APP_USER_NAME)",
    '-v', "dedicated_app_user_password=$($settings.RGSCENTER_DEDICATED_APP_USER_PASSWORD)",
    '-h', $settings.RGSCENTER_POSTGRES_DB_HOST,
    '-p', $settings.RGSCENTER_POSTGRES_DB_PORT,
    '-U', $settings.RGSCENTER_POSTGRES_DB_ROOTUSER,
    '-d', $settings.RGSCENTER_POSTGRES_DB_NAME,
    '-f', $sqlFile
)

$previousPgPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $settings.RGSCENTER_POSTGRES_DB_ROOTUSER_PASSWORD
    & $PsqlPath @psqlArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Database deployment failed; psql exit code: $LASTEXITCODE"
    }
} finally {
    $env:PGPASSWORD = $previousPgPassword
}

Write-Host "RGSCenter database '$($settings.RGSCENTER_DBNAME)' deployed successfully."
