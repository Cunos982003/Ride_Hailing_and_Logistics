$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    & .\mvnw.cmd -v
    if ($LASTEXITCODE -ne 0) { throw 'Maven wrapper failed. Set JAVA_HOME to JDK 21.' }
    & git config --local core.hooksPath .githooks
    if ($LASTEXITCODE -ne 0) { throw 'Could not enable pre-commit hook.' }
    Write-Host 'Enabled repository pre-commit hook. Run .\mvnw.cmd verify with JDK 21.'
} finally {
    Pop-Location
}
