[CmdletBinding()]
param(
    [switch] $CheckOnly,
    [switch] $SaveQuickCommerceKey,
    [switch] $ChangeQuickCommerceKey,
    [switch] $EnableQuickCommerce,
    [switch] $GoogleGeocodingDemo,
    [switch] $LocalGemini,
    [switch] $OnlineMaven,
    [int] $VerifiedDashboardBalance = 0,
    [ValidateRange(1024, 65535)]
    [int] $Port = 8080
)

$ErrorActionPreference = 'Stop'
$existingKey = [Environment]::GetEnvironmentVariable('QUICKCOMMERCE_API_KEY', 'Process')
$existingGeocoderProvider = $env:CARTIQ_GEOCODER_PROVIDER
$existingGoogleKey = $env:CARTIQ_GOOGLE_GEOCODING_API_KEY
$backendDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'backend'
$secretPath = Join-Path $backendDirectory '.env.local'
if ($ChangeQuickCommerceKey -and $PSBoundParameters.Count -ne 1) {
    throw 'Run -ChangeQuickCommerceKey by itself.'
}
if ($LocalGemini -and ($SaveQuickCommerceKey -or $GoogleGeocodingDemo -or
        $env:CARTIQ_RECIPE_GENERATION_ENABLED -ne 'true' -or !$env:AI_SERVICE_SECRET -or
        $env:AI_SERVICE_URL -notmatch '^http://127\.0\.0\.1:[0-9]+$' -or $env:GEMINI_API_KEY)) {
    throw 'Start the local Gemini pair through Start-CartIqGemini.ps1.'
}
if ($EnableQuickCommerce -and (-not $PSBoundParameters.ContainsKey('VerifiedDashboardBalance') -or $VerifiedDashboardBalance -lt 0)) {
    throw 'An enabled QuickCommerce launch requires an explicitly supplied, nonnegative verified balance.'
}
if ($GoogleGeocodingDemo -and ($EnableQuickCommerce -or $CheckOnly -or $SaveQuickCommerceKey)) {
    throw 'Run the Google geocoding demo as a standalone local launch.'
}

function Read-QuickCommerceKey {
    # Never execute dotenv content. Only accept this launcher's Windows-protected entry.
    $line = [IO.File]::ReadAllText($secretPath).TrimEnd("`r", "`n")
    if ($line -cnotmatch '^QUICKCOMMERCE_API_KEY=dpapi:([0-9a-fA-F]+)$') {
        throw 'The backend key file is in an unsupported format.'
    }
    try { ConvertTo-SecureString -String $Matches[1] }
    catch { throw 'The backend key file cannot be unlocked by this Windows account.' }
}

function Save-QuickCommerceKey([Security.SecureString] $secret, [switch] $Replace) {
    if ($secret.Length -eq 0) { throw 'The existing key was not entered.' }
    $staged = Join-Path $backendDirectory ('.env.local.' + [guid]::NewGuid().ToString('N'))
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = [Security.AccessControl.FileSecurity]::new()
    $acl.SetOwner($identity)
    $acl.SetAccessRuleProtection($true, $false)
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'Allow'))
    try {
        [IO.File]::Open($staged, [IO.FileMode]::CreateNew).Dispose()
        Set-Acl -LiteralPath $staged -AclObject $acl
        $protected = ConvertFrom-SecureString -SecureString $secret
        [IO.File]::WriteAllText($staged, "QUICKCOMMERCE_API_KEY=dpapi:$protected`n")
        if ($Replace -and [IO.File]::Exists($secretPath)) {
            [IO.File]::Replace($staged, $secretPath, [NullString]::Value)
        } else {
            [IO.File]::Move($staged, $secretPath)
        }
    } finally {
        [IO.File]::Delete($staged)
    }
}

$secret = $null
try {
    if ($ChangeQuickCommerceKey) {
        $secret = Read-Host 'Enter the QuickCommerceAPI key to use locally (hidden)' -AsSecureString
        Save-QuickCommerceKey $secret -Replace
        'QuickCommerceAPI key saved for this Windows account. Restart the local backend to load it.'
        return
    }
    if ($SaveQuickCommerceKey -and !(Test-Path -LiteralPath $secretPath -PathType Leaf)) {
        $entered = if ([string]::IsNullOrWhiteSpace($existingKey)) {
            Read-Host 'Enter the existing QuickCommerceAPI key' -AsSecureString
        } else { ConvertTo-SecureString -String $existingKey -AsPlainText -Force }
        try { Save-QuickCommerceKey $entered } finally { $entered.Dispose() }
    }
    if ((!$LocalGemini -or $EnableQuickCommerce) -and !$GoogleGeocodingDemo -and ($SaveQuickCommerceKey -or [string]::IsNullOrWhiteSpace($existingKey)) -and
        (Test-Path -LiteralPath $secretPath -PathType Leaf)) {
        $secret = Read-QuickCommerceKey
    }
    if ($CheckOnly -or $SaveQuickCommerceKey) {
        if (($null -ne $secret -and $secret.Length -gt 0) -or
            (!$SaveQuickCommerceKey -and ![string]::IsNullOrWhiteSpace($existingKey))) { 'PRESENT' } else { 'MISSING' }
        return
    }
    if ($null -ne $secret) {
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
        try {
            $env:QUICKCOMMERCE_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
        }
    }
    if ($EnableQuickCommerce -and [string]::IsNullOrWhiteSpace($env:QUICKCOMMERCE_API_KEY)) {
        'MISSING'
        return
    }
    if ($GoogleGeocodingDemo) {
        $env:CARTIQ_GEOCODER_PROVIDER = 'google-demo'
        if ([string]::IsNullOrWhiteSpace($existingGoogleKey)) {
            $googleSecret = Read-Host 'Enter the existing Google Maps Demo Key' -AsSecureString
            try {
                if ($googleSecret.Length -eq 0) { throw 'Google Maps Demo Key is missing.' }
                $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($googleSecret)
                try {
                    $env:CARTIQ_GOOGLE_GEOCODING_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
                } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
            } finally { $googleSecret.Dispose() }
        }
    }

    $maven = (Get-Command mvn.cmd -CommandType Application).Source
    [string[]] $mavenFlags = if (!$OnlineMaven) { @('-o') } else { @() }
    $databaseName = if ($GoogleGeocodingDemo) { 'geocoding-demo' } else { 'smartcart' }
    $databaseUrl = "jdbc:h2:file:./.runtime/$databaseName;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE"
    # Pass the key only through process inheritance. Command-line properties contain no credentials.
    $bootArguments = @(
        '--spring.profiles.active=local',
        '--spring.profiles.include=',
        '--server.address=127.0.0.1',
        "--server.port=$Port",
        "--spring.datasource.url=$databaseUrl",
        '--spring.datasource.driver-class-name=org.h2.Driver',
        '--spring.datasource.username=sa',
        '--spring.datasource.password=',
        "--spring.flyway.url=$databaseUrl",
        '--spring.flyway.user=sa',
        '--spring.flyway.password=',
        "--smartcart.quickcommerce.enabled=$($EnableQuickCommerce.IsPresent.ToString().ToLowerInvariant())",
        "--smartcart.quickcommerce.verified-start-balance=$VerifiedDashboardBalance",
        '--smartcart.quickcommerce.report-key-presence=true'
    )
    if ($GoogleGeocodingDemo) {
        $bootArguments += '--QUICKCOMMERCE_SHOPPING_ENABLED=false', '--QUICKCOMMERCE_PILOT_ENABLED=false'
    }
    Push-Location -LiteralPath $backendDirectory
    try {
        & $maven @mavenFlags "-Dspring-boot.run.arguments=$($bootArguments -join ' ')" spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw "Local Maven launch exited with code $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
} finally {
    if ($null -ne $secret) { $secret.Dispose() }
    [Environment]::SetEnvironmentVariable('QUICKCOMMERCE_API_KEY', $existingKey, 'Process')
    if ($GoogleGeocodingDemo) {
        [Environment]::SetEnvironmentVariable('CARTIQ_GEOCODER_PROVIDER', $existingGeocoderProvider, 'Process')
        [Environment]::SetEnvironmentVariable('CARTIQ_GOOGLE_GEOCODING_API_KEY', $existingGoogleKey, 'Process')
    }
}
