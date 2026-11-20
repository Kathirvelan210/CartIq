[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateRange(0, 2147483647)]
    [int] $VerifiedDashboardBalance,

    [string] $LedgerPath = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.cartiq/quickcommerce-budget.txt'),
    [switch] $ReconcilePending,
    [switch] $ConfirmedNoCharge,
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string] $RequestFingerprint,
    [guid] $ProviderRequestId,
    [ValidatePattern('^[a-z0-9][a-z0-9-]{0,99}$')]
    [string] $EvidenceReference
)

$ErrorActionPreference = 'Stop'
$attachEvidence = $PSBoundParameters.ContainsKey('RequestFingerprint') -or
    $PSBoundParameters.ContainsKey('ProviderRequestId') -or $PSBoundParameters.ContainsKey('EvidenceReference')
if ($ConfirmedNoCharge -and -not $attachEvidence) {
    throw 'Confirmed no-charge evidence requires the matching request fingerprint, provider request ID and evidence reference.'
}
if ($attachEvidence -and (-not $ReconcilePending -or $RequestFingerprint -cnotmatch '^[0-9a-f]{64}$' -or
    -not $PSBoundParameters.ContainsKey('ProviderRequestId') -or $ProviderRequestId -eq [guid]::Empty -or
    $EvidenceReference -cnotmatch '^[a-z0-9][a-z0-9-]{0,99}$')) {
    throw 'Evidence requires a pending reconciliation, a request fingerprint, a provider request ID, and an evidence reference.'
}
$LedgerPath = [IO.Path]::GetFullPath($LedgerPath)
$attemptLimit = 20
try {
    $policy = [IO.File]::ReadAllText("$LedgerPath.attempt-limit").Trim()
    if ($policy -cnotmatch '^[1-9][0-9]{0,5}$' -or [int]$policy -lt 20) { throw 'Invalid QuickCommerce attempt limit.' }
    $attemptLimit = [int]$policy
} catch [IO.FileNotFoundException] {
    # The original lifetime limit applies unless this ledger has an explicit extension.
} catch [IO.DirectoryNotFoundException] {
    # New ledger directory.
}
$stateDirectory = [IO.Path]::GetDirectoryName($LedgerPath)
[IO.Directory]::CreateDirectory($stateDirectory) | Out-Null

function Write-Ledger([string] $Contents, [bool] $Replace) {
    $staged = Join-Path $stateDirectory ('credit-preflight-' + [Guid]::NewGuid().ToString('N') + '.tmp')
    try {
        $stream = [IO.File]::Open($staged, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try {
            $bytes = [Text.Encoding]::UTF8.GetBytes($Contents)
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush($true)
        } finally {
            $stream.Dispose()
        }
        if ($Replace) { [IO.File]::Replace($staged, $LedgerPath, [NullString]::Value) }
        else { [IO.File]::Move($staged, $LedgerPath) }
    } finally {
        [IO.File]::Delete($staged)
    }
}

# Lock a byte in the range held by Java's whole-file lock. Keep the same lock file across runs.
$lock = [IO.File]::Open($LedgerPath + '.lock', [IO.FileMode]::OpenOrCreate,
    [IO.FileAccess]::ReadWrite, [IO.FileShare]::ReadWrite)
try {
    $lock.Lock(0, 1)
    $attempts = 0
    $action = 'Initialized'
    try {
        $lines = [IO.File]::ReadAllLines($LedgerPath)
    } catch [IO.FileNotFoundException] {
        $lines = $null
    }
    if ($null -ne $lines) {
        $recordedBalance = 0
        if ($lines.Length -lt 3 -or $lines.Length -gt (2 * $attemptLimit + 3) -or $lines[0] -cnotin @('cartiq-qc-budget-v1', 'cartiq-qc-budget-v2') -or
            -not [int]::TryParse($lines[1], [ref] $recordedBalance) -or $recordedBalance -lt 0 -or
            $lines[2] -cnotin @('settled', 'pending')) {
            throw 'Unrecognized QuickCommerce ledger. Preserve it and review it before continuing.'
        }
        $fingerprints = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        $evidencedAttempts = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        $requestIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        for ($i = 3; $i -lt $lines.Length; $i++) {
            if ($lines[$i] -cmatch '^[0-9a-f]{64}$' -and $fingerprints.Add($lines[$i])) { continue }
            if ($lines[0] -ceq 'cartiq-qc-budget-v2' -and
                $lines[$i] -cmatch '^evidence\t([0-9a-f]{64})\t([0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})\t[a-z0-9][a-z0-9-]{0,99}$' -and
                $evidencedAttempts.Add($Matches[1]) -and $requestIds.Add($Matches[2])) { continue }
            throw 'Malformed QuickCommerce ledger entry. Preserve the ledger and review it before continuing.'
        }
        if ($fingerprints.Count -gt $attemptLimit -or -not $evidencedAttempts.IsSubsetOf($fingerprints) -or
            ($lines[2] -ceq 'pending' -and $fingerprints.Count -eq $evidencedAttempts.Count)) {
            throw 'Invalid QuickCommerce attempt or evidence count.'
        }
        if ($attachEvidence) {
            # The legacy reservation stores an unordered set, so only an unambiguous unevidenced fingerprint can be attached.
            $expectedEvidenceBalance = [long]$recordedBalance + [int]$ConfirmedNoCharge.IsPresent
            if ($VerifiedDashboardBalance -ne $expectedEvidenceBalance -or
                $fingerprints.Count - $evidencedAttempts.Count -ne 1 -or
                -not $fingerprints.Contains($RequestFingerprint) -or $evidencedAttempts.Contains($RequestFingerprint) -or
                $requestIds.Contains($ProviderRequestId.ToString())) {
                throw 'The billing evidence does not match one unambiguous pending reservation.'
            }
        }
        $attempts = $fingerprints.Count
        if ($ReconcilePending) {
            if ($lines[2] -cne 'pending' -or $attempts -eq 0 -or $VerifiedDashboardBalance -lt $recordedBalance -or
                [long]$VerifiedDashboardBalance -gt ([long]$recordedBalance + 1)) {
                throw 'The verified balance cannot reconcile this ledger as a single pending attempt.'
            }
            # A cleared reservation still consumes one experiment attempt; the original fingerprint remains blocked.
            $lines[1] = "$VerifiedDashboardBalance"
            $lines[2] = 'settled'
            if ($attachEvidence) {
                $lines[0] = 'cartiq-qc-budget-v2'
                $lines += "evidence`t$RequestFingerprint`t$ProviderRequestId`t$EvidenceReference"
            }
            Write-Ledger (($lines -join "`n") + "`n") $true
            $action = 'Reconciled pending attempt'
        } elseif ($lines[2] -ceq 'pending' -or $recordedBalance -ne $VerifiedDashboardBalance) {
            throw 'QuickCommerce ledger has a pending reservation or does not match the verified balance. Preserve it and reconcile before any request.'
        } else {
            $action = 'Verified existing ledger'
        }
    } else {
        if ($ReconcilePending) { throw 'There is no existing ledger to reconcile.' }
        Write-Ledger "cartiq-qc-budget-v1`n$VerifiedDashboardBalance`nsettled`n" $false
    }
    [pscustomobject]@{
        Status = $action
        VerifiedBalance = $VerifiedDashboardBalance
        AttemptsRecorded = $attempts
        ExperimentCreditsLeft = $attemptLimit - $attempts
        AllowanceAboveFloor = [Math]::Min($attemptLimit - $attempts, $VerifiedDashboardBalance)
        ProviderRequests = 0
    }
} finally {
    $lock.Dispose()
}
