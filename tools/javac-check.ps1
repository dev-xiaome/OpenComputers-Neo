param(
    [string]$Project = 'D:\Workspace\Mods\1.21.1\OpenComputers Neo',
    [string]$Log = ''
)

# 用英文 + UTF-8 跑 javac，专门校验 `src/main/scala` 下混放的 .java。
#
# 为什么需要这个脚本：Gradle 的 Scala 插件（Zinc）在做联合编译时会用 **javac** 编译
# `src/main/scala` 里的 .java 文件；而 `scalac` 只把它们当签名读取、**不校验**。
# 所以 `tools/scalac-check-full.ps1` 报告 0 错误时，`gradlew compileScala` 仍可能因为
# 这些 .java 挂掉。这个脚本补上那块盲区。
#
# 另外：中文 Windows 上 javac 默认用 GBK 输出，经 Gradle 管道后按 UTF-8 解码会变成
# 完全不可读的乱码，所以这里强制 `-J-Duser.language=en -J-Dfile.encoding=UTF-8`。

$ErrorActionPreference = 'Stop'
$out = Join-Path $env:TEMP ("oc-javac-out-$PID")
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
$jars.Add((Join-Path $Project 'build\classes\scala\main'))
$cache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
Get-ChildItem $cache -Recurse -File -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match 'scala-library-2\.13|config-1\.4\.3' } |
    ForEach-Object { $jars.Add($_.FullName) }
Get-ChildItem (Join-Path $Project 'libs\maven-local') -Recurse -File -Filter 'OC-LuaJ-*.jar' -ErrorAction SilentlyContinue |
    ForEach-Object { $jars.Add($_.FullName) }
$cp = ($jars -join ';')

# 只校验混在 scala 源码目录里的 .java（那批才是盲区；src/main/java 由 compileJava 负责）
$files = Get-ChildItem (Join-Path $Project 'src\main\scala') -Recurse -File -Filter '*.java' |
    ForEach-Object { $_.FullName }
Write-Output ("java sources: {0}, classpath entries: {1}" -f $files.Count, $jars.Count)

$logPath = if ($Log -ne '') { $Log } else { Join-Path $env:TEMP 'oc-javac-check.log' }
Remove-Item $logPath -Force -ErrorAction SilentlyContinue

$javacArgs = @('-J-Duser.language=en', '-J-Duser.country=US', '-J-Dfile.encoding=UTF-8',
    '-encoding', 'UTF-8', '-Xmaxerrs', '5000', '-nowarn', '-proc:none', '-d', $out, '-cp', $cp) + $files
& javac @javacArgs 2>&1 | ForEach-Object { $_.ToString() } | Add-Content -Path $logPath -Encoding UTF8
$exit = $LASTEXITCODE

$errs = 0
if (Test-Path $logPath) {
    $errs = (Get-Content $logPath | Select-String -Pattern 'error:' | Measure-Object).Count
}
Write-Output ("javac exit: {0}, errors: {1}, log: {2}" -f $exit, $errs, $logPath)
exit $exit
