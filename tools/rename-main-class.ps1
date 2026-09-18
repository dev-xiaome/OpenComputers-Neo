# 把主类 `OpenComputers` 改名为 `OpenComputersNeo`（mod id 保持 `opencomputers_neo`）
#
# 为什么用词边界替换：`\bOpenComputers\b` 不会命中 `ModOpenComputers`、
# `ModPluginOpenComputers`、`opencomputers`（小写）等，避免误改集成类。
#
# 执行前请确保裁剪子代理已经结束（否则会和它们改同一批文件）。

$ErrorActionPreference = 'Stop'
$P   = 'D:\Workspace\Mods\1.21.1\OpenComputers Neo'
$enc = New-Object System.Text.UTF8Encoding($false)

# 1) 主类文件改名
$oldFile = Join-Path $P 'src\main\scala\li\cil\oc\OpenComputers.scala'
$newFile = Join-Path $P 'src\main\scala\li\cil\oc\OpenComputersNeo.scala'
if (Test-Path $oldFile) {
    Move-Item $oldFile $newFile -Force
    Write-Host "已改名文件: OpenComputers.scala -> OpenComputersNeo.scala"
} elseif (Test-Path $newFile) {
    Write-Host "文件已是 OpenComputersNeo.scala"
} else {
    throw "找不到主类文件"
}

# 2) 全项目标识符替换（scala / java / toml）
$changed = 0
Get-ChildItem (Join-Path $P 'src\main'), (Join-Path $P 'src\data') -Recurse -File -Include '*.scala','*.java','*.toml' -ErrorAction SilentlyContinue |
    ForEach-Object {
        $t = [IO.File]::ReadAllText($_.FullName)
        if ($t -match '\bOpenComputers\b') {
            [IO.File]::WriteAllText($_.FullName, ($t -replace '\bOpenComputers\b', 'OpenComputersNeo'), $enc)
            $changed++
        }
    }
Write-Host "已替换引用的文件数: $changed"

# 3) 字符串显示名修正：替换把 "OpenComputers" 变成 "OpenComputersNeo" 了，
#    凡是明显作为「显示名 / 日志名」出现的，恢复成带空格的 "OpenComputers Neo"
Get-ChildItem (Join-Path $P 'src\main'), (Join-Path $P 'src\data') -Recurse -File -Include '*.scala','*.java','*.toml' -ErrorAction SilentlyContinue |
    ForEach-Object {
        $t = [IO.File]::ReadAllText($_.FullName)
        $n = $t -replace '"OpenComputersNeo"', '"OpenComputers Neo"'
        if ($n -ne $t) { [IO.File]::WriteAllText($_.FullName, $n, $enc); Write-Host "  显示名修正: $($_.Name)" }
    }

Write-Host ''
Write-Host '完成。接下来验证：'
Write-Host '  $env:JAVA_TOOL_OPTIONS=''-Duser.language=en -Duser.country=US -Dfile.encoding=UTF-8'''
Write-Host '  cd ''D:\Workspace\Mods\1.21.1\OpenComputers Neo'''
Write-Host '  .\gradlew.bat compileScala --console=plain'
