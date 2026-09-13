# Convert 1.7.10 .lang files into 1.21.1 .json language files.
param(
    [Parameter(Mandatory = $true)][string]$LangDir
)
$LangDir = (Resolve-Path $LangDir).Path
$map = @{
    'en_US' = 'en_us'; 'zh_CN' = 'zh_cn'; 'zh_TW' = 'zh_tw'; 'de_DE' = 'de_de'
    'es_MX' = 'es_mx'; 'fr_FR' = 'fr_fr'; 'it_IT' = 'it_it'; 'pl_PL' = 'pl_pl'
    'pt_BR' = 'pt_br'; 'pt_PT' = 'pt_pt'; 'ru_RU' = 'ru_ru'
}
foreach ($f in Get-ChildItem $LangDir -File -Filter *.lang) {
    $base = [System.IO.Path]::GetFileNameWithoutExtension($f.Name)
    if (-not $map.ContainsKey($base)) { Write-Output "skip $($f.Name)"; continue }
    $out = Join-Path $LangDir ($map[$base] + '.json')
    $entries = [ordered]@{}
    $lines = [System.IO.File]::ReadAllLines($f.FullName, [System.Text.Encoding]::UTF8)
    foreach ($line in $lines) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $i = $t.IndexOf('=')
        if ($i -lt 1) { continue }
        $k = $t.Substring(0, $i).Trim()
        $v = $t.Substring($i + 1)
        $entries[$k] = $v
    }
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine('{')
    $keys = @($entries.Keys)
    for ($j = 0; $j -lt $keys.Count; $j++) {
        $k = $keys[$j]
        $v = $entries[$k]
        $kj = ($k | ConvertTo-Json -Compress)
        $vj = ($v | ConvertTo-Json -Compress)
        $comma = if ($j -lt $keys.Count - 1) { ',' } else { '' }
        [void]$sb.AppendLine("  $kj`: $vj$comma")
    }
    [void]$sb.AppendLine('}')
    [System.IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
    Remove-Item $f.FullName -Force
    Write-Output "$($f.Name) -> $($map[$base]).json ($($keys.Count) entries)"
}
