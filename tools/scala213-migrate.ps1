# Scala 2.11 -> 2.13 mechanical migration.
param(
    [Parameter(Mandatory = $true)][string]$Root,
    [string[]]$Include = @('*.scala')
)

$procPattern = '(?m)^(\s*(?:(?:override|private|protected|final|implicit)\s+)*def\s+[^\n={]*\))\s*\{\s*$'

$replacements = @(
    @('scala\.collection\.convert\.WrapAsScala\._', 'scala.jdk.CollectionConverters._'),
    @('scala\.collection\.convert\.WrapAsJava\._', 'scala.jdk.CollectionConverters._'),
    @('scala\.collection\.JavaConversions\._', 'scala.jdk.CollectionConverters._'),
    @('scala\.collection\.JavaConverters\._', 'scala.jdk.CollectionConverters._'),
    @('scala\.collection\.convert\.WrapAsScala', 'scala.jdk.CollectionConverters'),
    @('scala\.collection\.convert\.WrapAsJava', 'scala.jdk.CollectionConverters'),
    @('scala\.collection\.JavaConverters', 'scala.jdk.CollectionConverters'),
    @('\bTraversableOnce\b', 'IterableOnce'),
    @('\bGenTraversableOnce\b', 'IterableOnce'),
    @('\bTraversable\b', 'Iterable'),
    @('\btoStream\b', 'to(LazyList)'),
    @('\bscala\.compat\.Platform\.EOL\b', 'System.lineSeparator()')
)

$files = Get-ChildItem -Path $Root -Recurse -File -Include $Include
$changed = 0
$procCount = 0
foreach ($f in $files) {
    $text = [System.IO.File]::ReadAllText($f.FullName)
    $orig = $text
    $before = ([regex]::Matches($text, $procPattern)).Count
    if ($before -gt 0) {
        $text = [regex]::Replace($text, $procPattern, '${1}: Unit = {')
        $procCount += $before
    }
    foreach ($pair in $replacements) {
        $text = [regex]::Replace($text, $pair[0], $pair[1])
    }
    if ($text -ne $orig) {
        [System.IO.File]::WriteAllText($f.FullName, $text)
        $changed++
    }
}
Write-Output "rewritten files: $changed / $($files.Count); procedure defs converted: $procCount"
