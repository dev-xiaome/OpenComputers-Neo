# Run a gradle task, capture full output, and summarise compile errors per file.
param(
    [string]$Task = 'compileScala',
    [string]$Out = 'D:\Workspace\temp\oc-build.txt'
)
Set-Location 'D:\Workspace\Mods\1.21.1\OpenComputers Neo'
cmd /c "gradlew.bat $Task --console=plain --continue > `"$Out`" 2>&1"
$lines = Get-Content $Out
$errs = $lines | Select-String -Pattern '^(.*\.(scala|java)):(\d+): (error|错误)'
Write-Output ("total error lines: " + $errs.Count)
$errs | ForEach-Object {
    $m = $_.Matches[0]
    $p = $m.Groups[1].Value
    $i = $p.IndexOf('src\main\')
    if ($i -ge 0) { $p = $p.Substring($i + 9) }
    $p
} | Group-Object | Sort-Object Count -Descending | Select-Object Count, Name -First 60 | Format-Table -AutoSize | Out-String -Width 200
