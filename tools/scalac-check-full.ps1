param(
    [string]$Project = 'D:\Workspace\Mods\1.21.1\OpenComputers Neo',
    [string]$Packages = '',
    [string]$Log = ''
)

$ErrorActionPreference = 'Stop'
$out = Join-Path $env:TEMP ("oc-scalac-out-$PID")
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force -Path $out | Out-Null

$jars = New-Object System.Collections.Generic.List[string]
$manifest = Join-Path $Project 'build\tmp\createMinecraftArtifacts\nfrt_artifact_manifest.properties'
if (Test-Path $manifest) {
    Get-Content $manifest | Where-Object { $_ -match '^[^#].*=' } | ForEach-Object {
        $p = ($_ -split '=', 2)[1]
        $p = $p -replace '\\:', ':' -replace '\\\\', '\'
        if (Test-Path $p) { $jars.Add($p) }
    }
}
$jars.Add((Join-Path $Project 'build\moddev\artifacts\neoforge-21.1.244-merged.jar'))
$jars.Add((Join-Path $Project 'build\classes\java\main'))

$cache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
Get-ChildItem $cache -Recurse -File -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match 'scala-library-2\.13|scala-reflect-2\.13|config-1\.4\.3' } |
    ForEach-Object { $jars.Add($_.FullName) }
# OC-LuaJ 放在仓库内的本地 maven 目录（maven.cil.li 已不再托管这个 jar，见 gradle.properties）。
Get-ChildItem (Join-Path $Project 'libs\maven-local') -Recurse -File -Filter 'OC-LuaJ-*.jar' -ErrorAction SilentlyContinue | ForEach-Object { $jars.Add($_.FullName) }
$cp = ($jars -join ';')

$props = Get-Content (Join-Path $Project 'gradle.properties')
$line = $props | Where-Object { $_ -match '^scala_ported_packages=' }
$globs = ($line -replace '^scala_ported_packages=', '') -split ','
if ($Packages -ne '') { $globs = $Packages -split ',' }
$src = Join-Path $Project 'src\main\scala'
$files = New-Object System.Collections.Generic.List[string]
foreach ($g in $globs) {
    $g = $g.Trim()
    if ($g -eq '') { continue }
    if ($g.EndsWith('/**')) {
        $dir = Join-Path $src (($g -replace '/\*\*$', '') -replace '/', '\')
        Get-ChildItem -Path $dir -Recurse -File -Filter '*.scala' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -ne 'ExtendedLuaState.scala' } |
            ForEach-Object { if (-not $files.Contains($_.FullName)) { $files.Add($_.FullName) } }
    }
    elseif ($g.Contains('*')) {
        $pattern = Join-Path $src ($g -replace '/', '\')
        Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue |
            ForEach-Object { if (-not $files.Contains($_.FullName)) { $files.Add($_.FullName) } }
    }
    else {
        $p = Join-Path $src ($g -replace '/', '\')
        if ((Test-Path $p) -and (-not $files.Contains($p))) { $files.Add($p) }
    }
}
# OCCE keeps some .java files inside src/main/scala (e.g. common/blockentity/BlockEntityTypes.java).
# Those Java files reference Scala classes, so they cannot go through compileJava (which runs
# before compileScala). scalac can read .java sources for signature resolution, so pass them along.
Get-ChildItem $src -Recurse -File -Filter '*.java' -ErrorAction SilentlyContinue |
    ForEach-Object { if (-not $files.Contains($_.FullName)) { $files.Add($_.FullName) } }

Write-Output ("sources: {0}, classpath entries: {1}" -f $files.Count, $jars.Count)

$scalaJars = (Get-ChildItem $cache -Recurse -File -Filter 'scala-compiler-2.13.14.jar' -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
$scalaLib = (Get-ChildItem $cache -Recurse -File -Filter 'scala-library-2.13.14.jar' -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
$scalaRef = (Get-ChildItem $cache -Recurse -File -Filter 'scala-reflect-2.13.14.jar' -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
$compilerCp = "$scalaJars;$scalaLib;$scalaRef"

$scalaArgs = @('-d', $out, '-release', '21', '-encoding', 'UTF-8', '-nowarn', '-Xmaxerrs', '4000') + ($files | ForEach-Object { $_.Substring($Project.Length + 1) })
$log = if ($Log -ne '') { $Log } else { Join-Path $env:TEMP 'oc-scalac-check.log' }
$argFile = Join-Path $env:TEMP ("oc-scalac-args-$PID.txt")
[System.IO.File]::WriteAllLines($argFile, [string[]]$scalaArgs, (New-Object System.Text.UTF8Encoding($false)))
Remove-Item $log -Force -ErrorAction SilentlyContinue
$ErrorActionPreference = 'Continue'
$env:CLASSPATH = $cp
Set-Location $Project
& java -cp $compilerCp scala.tools.nsc.Main "@$argFile" 2>&1 | ForEach-Object { $_.ToString() } | Add-Content -Path $log -Encoding UTF8
$code = $LASTEXITCODE
$errs = 0
if (Test-Path $log) { $errs = (Select-String -Path $log -Pattern ': error:').Count }
Write-Output ("scalac exit: {0}, errors: {1}, log: {2}" -f $code, $errs, $log)
if ($errs -gt 0) { Select-String -Path $log -Pattern ': error:' | Select-Object -First 40 | ForEach-Object { $_.Line } }

# IMPORTANT: scalac runs in phases (parser -> namer -> typer -> refchecks -> ...).
# As soon as one phase reports errors, every later phase is SKIPPED. So a log with
# only a couple of parser/typer errors means refchecks never ran and the error
# count above is meaningless -- most Scala 2.13 strictness errors are refchecks
# errors ("Unit companion object is not allowed in source", "overrides nothing",
# "inherits conflicting members", "incompatible type in overriding", ...).
# Detect that situation explicitly instead of trusting the count.
if (Test-Path $log) {
    $phaseBlockers = (Select-String -Path $log -Pattern "unclosed comment|';' expected|error: not found:|error: type mismatch|is not a member of").Count
    if ($phaseBlockers -gt 0) {
        Write-Output ("WARNING: {0} parser/typer error(s) present -> refchecks did NOT run." -f $phaseBlockers)
        Write-Output "         The list above is NOT the real backlog. Fix these first, then re-run."
    }
}