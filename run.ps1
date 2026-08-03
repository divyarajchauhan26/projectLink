# Campus Connect — build and run.
#
#   .\run.ps1            build if needed, then launch
#   .\run.ps1 -Clean     force a full rebuild first
#   .\run.ps1 -Test      run the nine verification harnesses instead of the app
#
# Exists because the classpath is three jars long and nobody should have to
# remember it to open their own project.

param(
    [switch]$Clean,
    [switch]$Test
)

$ErrorActionPreference = "Stop"
$proj = Join-Path $PSScriptRoot "PorjectLink"
# Deliberately NOT "out": VS Code's Java extension owns that directory
# (java.project.outputPath in .vscode/settings.json). It recompiles continuously and
# will happily write class files it believes are broken -- those throw
# "Unresolved compilation problems" at runtime. Sharing the directory meant the script
# saw newer-than-source classes, skipped its own build, and ran the IDE's broken output.
$out  = Join-Path $proj "build"
$cp   = "build;lib/flatlaf-3.5.jar;lib/flatlaf-intellij-themes-3.5.jar;lib/gson-2.11.0.jar"

Push-Location $proj
try {
    if ($Clean -and (Test-Path $out)) {
        Remove-Item -Recurse -Force $out
    }

    # Rebuild when out/ is missing or any source is newer than the newest class.
    $needsBuild = -not (Test-Path $out)
    if (-not $needsBuild) {
        $newestSrc   = (Get-ChildItem -Recurse -Filter *.java src   | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
        $newestClass = (Get-ChildItem -Recurse -Filter *.class $out | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime
        if ($null -eq $newestClass -or $newestSrc -gt $newestClass) { $needsBuild = $true }
    }

    if ($needsBuild) {
        Write-Host "Building..." -ForegroundColor Cyan
        if (-not (Test-Path $out)) { New-Item -ItemType Directory $out | Out-Null }
        $sources = Get-ChildItem -Recurse -Filter *.java src | ForEach-Object FullName
        javac -d build -cp "lib/flatlaf-3.5.jar;lib/flatlaf-intellij-themes-3.5.jar;lib/gson-2.11.0.jar" $sources
        if ($LASTEXITCODE -ne 0) { throw "Build failed." }
        Write-Host "Built $((Get-ChildItem -Recurse -Filter *.class $out).Count) classes." -ForegroundColor Green
    }

    if ($Test) {
        $harnesses = @(
            "InterestCatalogHarness", "SeedHarness", "PhysicsHarness",
            "ViewportHarness", "MatchingHarness", "DiscoveryHarness", "InsightHarness",
            "GroupHarness", "ConnectionHarness", "UiHarness"
        )
        $failed = 0
        foreach ($h in $harnesses) {
            $result = (java -cp $cp "CampusConnect.dev.$h" | Select-Object -Last 1)
            if ($LASTEXITCODE -ne 0) { $failed++ }
            $colour = if ($LASTEXITCODE -eq 0) { "Green" } else { "Red" }
            Write-Host ("{0,-24} {1}" -f $h, $result) -ForegroundColor $colour
        }
        if ($failed -gt 0) { Write-Host "`n$failed harness(es) failed." -ForegroundColor Red }
        else { Write-Host "`nAll harnesses passed." -ForegroundColor Green }
    }
    else {
        Write-Host "Launching Campus Connect..." -ForegroundColor Cyan
        java -cp $cp CampusConnect.main.Main
    }
}
finally {
    Pop-Location
}
