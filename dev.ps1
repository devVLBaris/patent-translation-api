param([ValidateSet('run', 'test', 'package')][string]$Task = 'run')
$ErrorActionPreference = 'Stop'

# Support a normal JAVA_HOME/PATH setup and IntelliJ-managed local installations.
if (-not $env:JAVA_HOME) {
    $jdkRoot = Join-Path $env:USERPROFILE '.jdks'
    $jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin/javac.exe') } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($maven) {
    $mavenPath = $maven.Source
} else {
    $mavenPath = Get-ChildItem -Path "$env:ProgramFiles/JetBrains/IntelliJ*/plugins/maven/lib/maven3/bin/mvn.cmd" `
        -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName -First 1
}
if (-not $mavenPath) { throw 'Maven not found. Install Maven and add its bin directory to PATH.' }

Push-Location $PSScriptRoot
try {
    switch ($Task) {
        'run' { & $mavenPath spring-boot:run }
        'test' { & $mavenPath test }
        'package' { & $mavenPath clean verify }
    }
    if ($LASTEXITCODE -ne 0) { throw "Maven exited with code $LASTEXITCODE" }
} finally {
    Pop-Location
}
