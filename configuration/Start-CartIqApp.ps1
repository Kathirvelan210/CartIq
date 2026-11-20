[CmdletBinding()]
param([switch] $NoBrowser, [switch] $RunBackend)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$web = Join-Path $env:LOCALAPPDATA 'CartIQ\active-shopping\web'
$backendLauncher = Join-Path $PSScriptRoot 'Start-CartIqBackend.ps1'

if ($RunBackend) {
    # This interactive window owns the backend. Ctrl+C stops the local session.
    $Host.UI.RawUI.WindowTitle = 'CartIQ local backend'
    Get-Command mvn.cmd, java.exe -ErrorAction Stop | Out-Null
    $balance = Read-Host 'Live shopping: enter the verified QuickCommerce dashboard balance, or press Enter to leave it off'
    $enableShopping = ![string]::IsNullOrWhiteSpace($balance)
    $options = @{}
    if ($enableShopping) {
        $amount = 0
        if (![int]::TryParse($balance, [ref]$amount) -or $amount -lt 0) { throw 'Enter a nonnegative whole credit balance.' }
        if (Test-Path (Join-Path $root 'backend\.env.local')) { Remove-Item Env:QUICKCOMMERCE_API_KEY -ErrorAction SilentlyContinue }
        if ((& $backendLauncher -CheckOnly) -ne 'PRESENT') { throw 'Run Change-QuickCommerce-Key.bat to configure the backend key.' }
        $ledger = [IO.File]::ReadAllLines((Join-Path $env:USERPROFILE '.cartiq\quickcommerce-budget.txt'))
        if ($ledger.Count -lt 3 -or $ledger[2] -cne 'settled' -or $ledger[1] -ne "$amount") {
            throw 'The verified dashboard balance must match a settled local ledger.'
        }
        $options.VerifiedDashboardBalance = $amount
    }
    $enableAi = $env:GEMINI_API_KEY -or [Environment]::GetEnvironmentVariable('CARTIQ_GEMINI_KEY_DPAPI','User') -or
        (Test-Path -LiteralPath (Join-Path $env:LOCALAPPDATA 'CartIQ\gemini-key.dpapi') -PathType Leaf)
    if (!$enableAi) { $enableAi = (Read-Host 'Enable AI recipes? [y/N]') -in 'y','yes' }
    if ($enableAi) {
        & (Join-Path $PSScriptRoot 'Start-CartIqGemini.ps1') -WithLocalShopping:$enableShopping -PromptForExistingKey -AiPort 18001 @options
    } else {
        Remove-Item Env:GEMINI_API_KEY -ErrorAction SilentlyContinue
        $env:CARTIQ_RECIPE_GENERATION_ENABLED = 'false'
        if ($enableShopping) {
            $env:QUICKCOMMERCE_SHOPPING_ENABLED = 'true'
            $env:QUICKCOMMERCE_PILOT_ENABLED = 'true'
            $env:QUICKCOMMERCE_LEGACY_LOOKUPS_ENABLED = 'false'
        }
        if (!$env:JWT_SECRET) {
            $bytes = New-Object byte[] 48
            $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $rng.GetBytes($bytes); $env:JWT_SECRET = [Convert]::ToBase64String($bytes) } finally { $rng.Dispose() }
        }
        & $backendLauncher -EnableQuickCommerce:$enableShopping @options
    }
    return
}

function Test-Backend {
    try {
        $identity = Invoke-RestMethod 'http://127.0.0.1:8080/' -Headers @{Accept='application/json'} -TimeoutSec 3 -MaximumRedirection 0
        return $identity.name -eq 'CartIQ Backend API' -and (Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 3).status -eq 'UP'
    } catch { return $false }
}
function Test-Web {
    try { return (Invoke-WebRequest 'http://localhost:8092/' -UseBasicParsing -TimeoutSec 3 -MaximumRedirection 0).Content -match '<title>CartIQ</title>' }
    catch { return $false }
}
function Assert-FreePort([int]$port) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "Port $port is occupied but CartIQ is not ready. Check the existing service and run again."
    }
}

if (!(Test-Web)) {
    Assert-FreePort 8092
    $node = (Get-Command node.exe).Source
    # Keep provider credentials out of the web build and static server.
    $providerKeys = @{}
    foreach ($name in 'QUICKCOMMERCE_API_KEY','GEMINI_API_KEY','CARTIQ_GOOGLE_GEOCODING_API_KEY','JWT_SECRET','AI_SERVICE_SECRET') {
        $providerKeys[$name] = [Environment]::GetEnvironmentVariable($name)
        [Environment]::SetEnvironmentVariable($name,$null,'Process')
    }
    try {
        if (!(Test-Path -LiteralPath (Join-Path $web 'index.html'))) {
            Push-Location -LiteralPath $root
            try {
                if (!(Test-Path 'node_modules\.bin\expo.cmd')) { throw 'Run npm ci in the project folder first.' }
                $env:EXPO_PUBLIC_API_URL = '/api'
                $env:EXPO_NO_DOTENV = '1'
                $env:EXPO_NO_TELEMETRY = '1'
                & '.\node_modules\.bin\expo.cmd' export --platform web --output-dir $web
                if ($LASTEXITCODE -ne 0) { throw 'Expo web build failed.' }
            } finally { Pop-Location }
        }
        $webServer = Join-Path $PSScriptRoot 'Serve-LocalWeb.cjs'
        $webProcess = Start-Process $node -ArgumentList ('"{0}" "{1}" 8092' -f $webServer,$web) -WorkingDirectory $root -WindowStyle Hidden -PassThru
        for ($i=0; $i -lt 15 -and !(Test-Web) -and !$webProcess.HasExited; $i++) { Start-Sleep -Milliseconds 500 }
        if (!(Test-Web)) { throw 'The local CartIQ web server failed to start on port 8092.' }
    } finally {
        foreach ($name in $providerKeys.Keys) { [Environment]::SetEnvironmentVariable($name,$providerKeys[$name],'Process') }
    }
}
if (!(Test-Backend)) {
    Assert-FreePort 8080
    Write-Host 'Complete the startup choices in the CartIQ backend window.'
    $backend = Start-Process powershell.exe -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -File "{0}" -RunBackend' -f $PSCommandPath) -WorkingDirectory $root -PassThru
    $deadline = [DateTime]::UtcNow.AddMinutes(10)
    while (!(Test-Backend)) {
        if ($backend.HasExited) { throw "The CartIQ backend exited before becoming healthy (code $($backend.ExitCode))." }
        if ([DateTime]::UtcNow -gt $deadline) { throw 'Startup is still pending. Check the CartIQ backend window for a prompt or startup error.' }
        Start-Sleep -Seconds 2
    }
}
Write-Host 'CartIQ is ready. Frontend: http://localhost:8092/   Backend: http://127.0.0.1:8080/'
if (!$NoBrowser) { Start-Process 'http://localhost:8092/' }
