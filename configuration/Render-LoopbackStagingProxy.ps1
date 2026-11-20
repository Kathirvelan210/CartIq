param(
    [Parameter(Mandatory)][string] $NginxDirectory,
    [Parameter(Mandatory)][string] $WebDirectory,
    [Parameter(Mandatory)][string] $TlsDirectory
)

$ErrorActionPreference = 'Stop'
$web = (Resolve-Path -LiteralPath $WebDirectory).Path.Replace([char]92, [char]47)
$tls = (Resolve-Path -LiteralPath $TlsDirectory).Path.Replace([char]92, [char]47)
$nginx = (Resolve-Path -LiteralPath $NginxDirectory).Path
if (!(Test-Path -LiteralPath (Join-Path $web 'index.html'))) { throw 'Web export is missing.' }
foreach ($file in 'fullchain.pem', 'privkey.pem') {
    if (!(Test-Path -LiteralPath (Join-Path $tls $file) -PathType Leaf)) { throw 'TLS files are missing.' }
}

$template = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'nginx.conf'))
$config = $template.Replace('${CARTIQ_HTTPS_ORIGIN}', 'https://localhost:18443')
$config = $config.Replace('listen 80 default_server;', 'listen 127.0.0.1:18081 default_server;')
$config = $config.Replace('listen 443 ssl default_server;', 'listen 127.0.0.1:18443 ssl default_server;')
$config = $config.Replace('/etc/cartiq-tls/fullchain.pem', "`"$tls/fullchain.pem`"")
$config = $config.Replace('/etc/cartiq-tls/privkey.pem', "`"$tls/privkey.pem`"")
$config = $config.Replace('/usr/share/nginx/html', "`"$web`"")
$config = $config.Replace('/var/log/nginx/access.log', 'logs/staging-access.log')
$config = $config.Replace('/dev/null', 'logs/staging-edge-errors.log')
$config = $config -replace '(?m)^ +resolver 127\.0\.0\.11.*\r?\n', ''
$config = $config.Replace('http://backend:8080', 'http://127.0.0.1:18932')
[IO.File]::WriteAllText((Join-Path $nginx 'conf/cartiq-staging.conf'),
    "worker_processes 1;`nevents { worker_connections 128; }`nhttp { include mime.types;`n$config`n}`n")

Push-Location -LiteralPath $nginx
try {
    & ./nginx.exe -t -c conf/cartiq-staging.conf
    if ($LASTEXITCODE -ne 0) { throw 'Nginx configuration validation failed.' }
} finally {
    Pop-Location
}
