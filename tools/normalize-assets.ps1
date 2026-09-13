param(
    [string]$Project = 'D:\Workspace\Mods\1.21.1\OpenComputers Neo'
)
# Normalize assets so that every path used in a ResourceLocation is lowercase.
# 1.21.1 ResourceLocation paths only allow [a-z0-9/._-]; uppercase letters crash the
# resource reload (and DeferredRegister.register) at startup.
$ErrorActionPreference = 'Stop'
$assets = Join-Path $Project 'src\main\resources\assets\opencomputers_neo'
if (-not (Test-Path $assets)) { throw "assets dir not found: $assets" }

function Rename-ToLower([string]$dir) {
    if (-not (Test-Path $dir)) { return 0 }
    $n = 0
    # Deepest first so directory renames (none today) would not break child paths.
    Get-ChildItem $dir -Recurse -File | ForEach-Object {
        $lower = $_.Name.ToLowerInvariant()
        if ($lower -cne $_.Name) {
            $target = Join-Path $_.DirectoryName $lower
            $tmp = $_.FullName + '.tmpren'
            [System.IO.File]::Move($_.FullName, $tmp)
            [System.IO.File]::Move($tmp, $target)
            $n++
        }
    }
    return $n
}

$renamed = 0
foreach ($sub in @('textures', 'models', 'blockstates', 'sounds')) {
    $renamed += Rename-ToLower (Join-Path $assets $sub)
}
Write-Output "renamed files: $renamed"

# Rewrite `opencomputers_neo:<path>` references inside json files to lowercase paths.
$jsonFiles = Get-ChildItem $assets -Recurse -File -Include *.json, *.mcmeta
$changed = 0
foreach ($f in $jsonFiles) {
    $text = [System.IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $new = [regex]::Replace($text, 'opencomputers_neo:([A-Za-z0-9/._-]*)', {
        param($m)
        'opencomputers_neo:' + $m.Groups[1].Value.ToLowerInvariant()
    })
    if ($new -cne $text) {
        [System.IO.File]::WriteAllText($f.FullName, $new, (New-Object System.Text.UTF8Encoding($false)))
        $changed++
    }
}
Write-Output "rewritten json files: $changed / $($jsonFiles.Count)"

# Report anything still containing uppercase that would break a ResourceLocation.
$remaining = Get-ChildItem (Join-Path $assets 'textures'), (Join-Path $assets 'models'), (Join-Path $assets 'blockstates') -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -cmatch '[A-Z]' }
Write-Output "remaining uppercase file names: $($remaining.Count)"
