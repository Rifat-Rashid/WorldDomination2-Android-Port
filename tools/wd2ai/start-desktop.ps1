param([Parameter(Mandatory=$true)][string]$Python, [int]$Port=8794)
$ErrorActionPreference = "Stop"
$Python = (Get-Item -LiteralPath $Python).FullName
$state = Join-Path $env:LOCALAPPDATA "WD2-AI"
New-Item -ItemType Directory -Force -Path $state | Out-Null
$keyFile = Join-Path $state "bridge.dpapi"
if (!(Test-Path -LiteralPath $keyFile)) {
    $bytes = New-Object byte[] 32
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    $key = ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
    ConvertFrom-SecureString $key | Set-Content -LiteralPath $keyFile
}
$key = (Get-Content -Raw -LiteralPath $keyFile).Trim() | ConvertTo-SecureString
$env:WD2_MCP_TOKEN = [Net.NetworkCredential]::new("",$key).Password
$child = $null
try {
    $entry = Join-Path $PSScriptRoot "server.py"
    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    if ($listener) {
        $service = Get-CimInstance Win32_Process -Filter "ProcessId = $($listener.OwningProcess)"
        # Windows venv launchers start the base interpreter as their child.
        if ($listener.LocalAddress -ne "127.0.0.1" -or !$service.CommandLine.Contains($entry)) {
            throw "Port $Port is in use by a different service"
        }
    } else {
        $child = Start-Process -FilePath $Python -ArgumentList @(('"' + $entry + '"'),"--http","--port",$Port) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru -RedirectStandardError (Join-Path $state "server.log")
        for ($i=0; $i -lt 50; $i++) {
            if ($child.HasExited) { throw "Bridge exited. See $state/server.log" }
            if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { break }
            Start-Sleep -Milliseconds 200
        }
    }
    & $Python (Join-Path $PSScriptRoot "register_desktop.py") --port $Port
    if ($LASTEXITCODE) { throw "Desktop registration failed" }
} finally {
    Remove-Item Env:WD2_MCP_TOKEN
}
