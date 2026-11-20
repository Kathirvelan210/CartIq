[CmdletBinding()]
param(
    [switch] $CheckOnly,
    [switch] $WithLocalShopping,
    [switch] $ReplaceLocalShoppingBackend,
    [ValidateRange(0,2147483647)][int] $VerifiedDashboardBalance,
    [switch] $PromptForExistingKey,
    [switch] $SaveKeyOnly,
    [string] $ProtectedKeyFile = (Join-Path $env:LOCALAPPDATA 'CartIQ\gemini-key.dpapi'),
    [string] $Model = $env:GEMINI_MODEL,
    [ValidateRange(1024,65535)][int] $AiPort = 8001,
    [ValidateRange(1024,65535)][int] $BackendPort = 8080
)

$ErrorActionPreference = 'Stop'
$protectedVariable = 'CARTIQ_GEMINI_KEY_DPAPI'
function Save-UserGeminiKey([Security.SecureString]$value) {
    if (!$value -or !$value.Length) { throw 'No key entered. The saved configuration was not changed.' }
    [Environment]::SetEnvironmentVariable($protectedVariable,(ConvertFrom-SecureString $value),'User')
}
if ($SaveKeyOnly) {
    $entered = Read-Host 'Save your Gemini API key for future CartIQ starts (hidden)' -AsSecureString
    try { Save-UserGeminiKey $entered } finally { if ($entered) { $entered.Dispose() } }
    Write-Output 'Gemini key saved for this Windows account.'
    return
}
$aiDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'ai-service'
$python = Join-Path $aiDirectory '.venv\Scripts\python.exe'
if (!(Test-Path -LiteralPath $python)) { throw 'Install the CartIQ AI service virtual environment first.' }
if ($AiPort -eq $BackendPort) { throw 'Choose different local ports for Spring and FastAPI.' }
if (!$Model) { $Model = 'gemini-3.5-flash' }
if ($Model -cnotmatch '^gemini-[0-9]+\.[0-9]+-flash(?:-lite)?$') { throw 'Invalid Gemini Flash model configuration.' }

if ($WithLocalShopping -and !$PSBoundParameters.ContainsKey('VerifiedDashboardBalance')) {
    throw 'Supply the last verified shopping balance for the local shopping launcher.'
}
if ($ReplaceLocalShoppingBackend -and !$WithLocalShopping) { throw 'Replacement requires the local shopping configuration.' }

function Get-LocalShoppingProcess {
    $listener = @(Get-NetTCPConnection -State Listen -LocalPort $BackendPort -ErrorAction SilentlyContinue)
    if (!$listener.Count) { return }
    $ownerProcess = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $listener[0].OwningProcess)
    $projectBackend = Join-Path (Split-Path -Parent $PSScriptRoot) 'backend'
    if ($listener.Count -ne 1 -or $listener[0].LocalAddress -ne '127.0.0.1' -or
        $ownerProcess.Name -ne 'java.exe' -or
        !$ownerProcess.CommandLine.Contains('--spring.profiles.active=local') -or
        !$ownerProcess.CommandLine.Contains('--smartcart.quickcommerce.enabled=true') -or
        !$ownerProcess.CommandLine.Contains('--spring.datasource.url=jdbc:h2:file:./.runtime/smartcart;')) {
        throw 'The backend port is occupied by a different service.'
    }
    if (!$ownerProcess.CommandLine.Contains($projectBackend)) {
        # Maven can shorten the Java command line to an argument file on Windows.
        $jcmd = Join-Path (Split-Path $ownerProcess.ExecutablePath) 'jcmd.exe'
        if (!(Test-Path -LiteralPath $jcmd)) { throw 'Cannot verify the local backend working directory.' }
        $cwdLine = (& $jcmd $ownerProcess.ProcessId VM.system_properties | Select-String '^user\.dir=').Line
        if (!$cwdLine -or $cwdLine.Substring(9).Replace('\\','\').Replace('\:',':') -ne $projectBackend) {
            throw 'The running backend belongs to another checkout.'
        }
    }
    return $ownerProcess.ProcessId
}

function New-LocalProcess([string]$exe,[string]$arguments,[string]$directory) {
    $info = [Diagnostics.ProcessStartInfo]::new($exe,$arguments)
    $info.WorkingDirectory = $directory
    $info.UseShellExecute = $false
    # Both processes inherit runtime tools; only FastAPI receives the model key.
    $info.EnvironmentVariables.Clear()
    foreach ($name in 'PATH','PATHEXT','SystemRoot','windir','ComSpec','USERPROFILE','HOME','TEMP','TMP',
             'APPDATA','LOCALAPPDATA','ProgramFiles','ProgramFiles(x86)','JAVA_HOME','MAVEN_HOME','NUMBER_OF_PROCESSORS') {
        $value = [Environment]::GetEnvironmentVariable($name)
        if ($value) { $info.EnvironmentVariables[$name] = $value }
    }
    $info.EnvironmentVariables['AI_SERVICE_SECRET'] = $serviceToken
    $info.EnvironmentVariables['CARTIQ_RECIPE_GENERATION_ENABLED'] = 'true'
    return $info
}

function New-SessionSecret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes); return [Convert]::ToBase64String($bytes) }
    finally { $rng.Dispose(); [Array]::Clear($bytes,0,$bytes.Length) }
}
$serviceToken = $env:AI_SERVICE_SECRET
if (!$serviceToken) { $serviceToken = New-SessionSecret }
$key = $env:GEMINI_API_KEY
$secret = $null
$ai = $null
try {
    $saved = [Environment]::GetEnvironmentVariable($protectedVariable,'User')
    if (!$key -and $saved) {
        try { $secret = ConvertTo-SecureString -String $saved -ErrorAction Stop }
        catch { throw 'Cannot unlock the saved CartIQ Gemini key. Run Save-Gemini-Key.bat under this Windows account.' }
    } elseif (!$key -and (Test-Path -LiteralPath $ProtectedKeyFile -PathType Leaf)) {
        try {
            $blob = [IO.File]::ReadAllText($ProtectedKeyFile).Trim() -creplace '^GEMINI_API_KEY=dpapi:', ''
            $secret = ConvertTo-SecureString -String $blob -ErrorAction Stop
        } catch { throw 'The supplied Windows-protected Gemini key could not be unlocked.' }
    } elseif (!$key -and $PromptForExistingKey) {
        $secret = Read-Host 'Existing Gemini API key (hidden, saved for future CartIQ starts)' -AsSecureString
        Save-UserGeminiKey $secret
    }
    if ($null -ne $secret) { $key = ([PSCredential]::new('CartIQ',$secret)).GetNetworkCredential().Password }
    $probe = New-LocalProcess $python '-B -c "import json; from recipe_generation import generation_readiness; print(json.dumps(generation_readiness()))"' $aiDirectory
    $probe.EnvironmentVariables['GEMINI_MODEL'] = $Model
    if ($key) { $probe.EnvironmentVariables['GEMINI_API_KEY'] = $key }
    $probe.RedirectStandardOutput = $true
    $child = [Diagnostics.Process]::Start($probe)
    try {
        $report = $child.StandardOutput.ReadToEnd() | ConvertFrom-Json
        $child.WaitForExit()
        if ($child.ExitCode -ne 0 -or !$report) { throw 'FastAPI configuration check failed.' }
    } finally { $child.Dispose() }
    $report | ConvertTo-Json -Compress
    if (!$report.ready) { throw 'Gemini activation is blocked. Use the existing key through the secure local prompt.' }
    if ($CheckOnly) { return }

    $replaceId = if ($ReplaceLocalShoppingBackend) { Get-LocalShoppingProcess }
    foreach ($port in $AiPort,$BackendPort) {
        if ($port -eq $BackendPort -and $replaceId) { continue }
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port)
        try { $listener.Start() } catch { throw "Local port $port is already in use." } finally { $listener.Stop() }
    }
    $probe.Arguments = "-B -m uvicorn app:app --host 127.0.0.1 --port $AiPort --no-access-log"
    $probe.RedirectStandardOutput = $false
    $ai = [Diagnostics.Process]::Start($probe)
    $probe.EnvironmentVariables.Remove('GEMINI_API_KEY')
    $key = $null
    $ready = $false
    for ($attempt=0; $attempt -lt 45 -and !$ai.HasExited; $attempt++) {
        try {
            $state = Invoke-RestMethod -Uri "http://127.0.0.1:$AiPort/recipes/readiness" -TimeoutSec 2 -MaximumRedirection 0 -Headers @{'X-Service-Token'=$serviceToken}
            $ready = $state.ready -and $state.model -eq $Model
        } catch { $ready = $false }
        if ($ready) { break }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready -or $ai.HasExited) { throw 'Private FastAPI startup or service authentication failed.' }
    Write-Output "FastAPI ready on loopback port $AiPort. No Gemini request sent."
    $pwsh = (Get-Process -Id $PID).Path
    $launcher = Join-Path $PSScriptRoot 'Start-CartIqBackend.ps1'
    $springArgs = "-NoProfile -File `"$launcher`" -LocalGemini -Port $BackendPort"
    if ($WithLocalShopping) {
        if ((& $launcher -CheckOnly) -ne 'PRESENT') { throw 'Local shopping credential is unavailable.' }
        $ledger = [IO.File]::ReadAllLines((Join-Path $env:USERPROFILE '.cartiq/quickcommerce-budget.txt'))
        if ($ledger[2] -cne 'settled' -or $ledger[1] -ne "$VerifiedDashboardBalance") {
            throw 'The shopping ledger changed. Check it before restarting.'
        }
        $springArgs += " -EnableQuickCommerce -VerifiedDashboardBalance $VerifiedDashboardBalance"
    }
    $spring = New-LocalProcess $pwsh $springArgs $PSScriptRoot
    if ($WithLocalShopping) {
        $spring.EnvironmentVariables['QUICKCOMMERCE_SHOPPING_ENABLED']='true'
        $spring.EnvironmentVariables['QUICKCOMMERCE_PILOT_ENABLED']='true'
        $spring.EnvironmentVariables['QUICKCOMMERCE_LEGACY_LOOKUPS_ENABLED']='false'
        if ($env:QUICKCOMMERCE_API_KEY) { $spring.EnvironmentVariables['QUICKCOMMERCE_API_KEY']=$env:QUICKCOMMERCE_API_KEY }
    }
    $spring.EnvironmentVariables['AI_SERVICE_URL'] = "http://127.0.0.1:$AiPort"
    $spring.EnvironmentVariables['JWT_SECRET'] = if ($env:JWT_SECRET) { $env:JWT_SECRET } else { New-SessionSecret }
    if ($replaceId) {
        if ((Get-LocalShoppingProcess) -ne $replaceId) { throw 'The local backend process changed during setup.' }
        Stop-Process -Id $replaceId -ErrorAction Stop
        Wait-Process -Id $replaceId -Timeout 15 -ErrorAction SilentlyContinue
    }
    $backend = [Diagnostics.Process]::Start($spring)
    try { $backend.WaitForExit(); if ($backend.ExitCode -ne 0) { throw 'Local Spring startup failed.' } }
    finally { if (!$backend.HasExited) { & taskkill.exe /PID $backend.Id /T /F | Out-Null }; $backend.Dispose() }
} finally {
    if ($null -ne $ai) {
        if (!$ai.HasExited) { & taskkill.exe /PID $ai.Id /T /F | Out-Null }
        $ai.Dispose()
    }
    if ($null -ne $secret) { $secret.Dispose() }
}
