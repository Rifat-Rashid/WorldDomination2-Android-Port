param(
    [Parameter(Mandatory = $true)][ValidateSet('build','init','observe','choose','status','replay','checkpoint','continue','stream','compare')][string]$Command,
    [string]$Log,
    [string]$Episode,
    [string]$Family,
    [long]$Seed = 1,
    [int]$Difficulty = 1,
    [int]$Actor = 1,
    [string]$Policy = 'baseline',
    [string]$HumanPolicy = 'pressure',
    [string]$Preference = 'Preserve citizens, exploit threats, and build sustainable fighting strength.',
    [string]$Leaders = '0,1,2,3',
    [string]$Teacher = 'manual',
    [string]$Plan,
    [string]$Reason,
    [string]$ExpectDecision,
    [int]$MaxTurns = 150,
    [int]$At = -1,
    [string]$Out
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$classes = Join-Path $repo 'analysis/wd2play/classes'
if ($Command -eq 'build') {
    $game = Join-Path $repo 'app/src/main/java/com/example/worlddomination/game'
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $game 'simulation') -Filter '*.java' -File | ForEach-Object FullName)
    $sources += (Join-Path $game 'ai/OpponentAI.java'), (Join-Path $game 'ai/DecisionRequest.java'), `
        (Join-Path $game 'persistence/GameSaveCodec.java'), (Join-Path $PSScriptRoot 'Play.java')
    $sourceManifest = ($sources | Sort-Object | ForEach-Object {
        $relative = $_.Substring($repo.Length + 1).Replace('\', '/')
        "$relative $((Get-FileHash -Algorithm SHA256 -LiteralPath $_).Hash.ToLowerInvariant())"
    }) -join "`n"
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $fingerprint = [BitConverter]::ToString($sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($sourceManifest)))
        $fingerprint = $fingerprint.Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
    $staging = Join-Path $repo ('analysis/wd2play/builds/' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $staging -Force | Out-Null
    & javac --release 11 -encoding UTF-8 -d $staging @sources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    [IO.File]::WriteAllText((Join-Path $staging 'wd2-engine-sources.sha256'), $fingerprint)
    [IO.File]::WriteAllText((Join-Path $staging 'wd2-engine-sources.manifest'), $sourceManifest)
    # javac writes class files progressively. Keep active collectors away from
    # that output and publish each completed file with an atomic replacement.
    $compiled = Get-ChildItem -LiteralPath $staging -Recurse -File | Sort-Object { $_.Extension -ne '.class' }
    foreach ($file in $compiled) {
        $target = Join-Path $classes $file.FullName.Substring($staging.Length + 1)
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        $ready = $file.FullName + '.ready'
        [IO.File]::Copy($file.FullName, $ready)
        if ([IO.File]::Exists($target)) {
            $backup = $file.FullName + '.replaced'
            [IO.File]::Replace($ready, $target, $backup)
        } else {
            [IO.File]::Move($ready, $target)
        }
    }
    exit 0
}
if (!(Test-Path -LiteralPath (Join-Path $classes 'com/example/worlddomination/game/ai/Play.class'))) {
    throw 'Compile the harness with play.ps1 -Command build first.'
}
$main = 'com.example.worlddomination.game.ai.Play'
switch ($Command) {
    'init' {
        $engineCommit = & git -C $repo rev-parse HEAD
        & java -cp $classes $main $Command $Log $Episode $Family $Seed $Difficulty $Actor `
            $Policy $HumanPolicy $Preference $Leaders $Teacher $MaxTurns $engineCommit
    }
    'choose' { & java -cp $classes $main $Command $Log $Plan $Reason $ExpectDecision }
    'checkpoint' { & java -cp $classes $main $Command $Log $At $Out }
    default { & java -cp $classes $main $Command $Log }
}
exit $LASTEXITCODE
