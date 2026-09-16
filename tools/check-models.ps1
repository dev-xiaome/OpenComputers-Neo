# 模型引用兜底校验（OpenComputers Neo / 1.21.1）
#
# 作用：扫描 assets/opencomputers_neo/models 下所有 .json（含 blockstates），
#       把其中出现的 "opencomputers_neo:xxx" 引用逐个解析成真实文件路径并检查是否存在，
#       按引用类型分类报告：
#         textures  —— 贴图引用（textures 段里的值，以及面里的 #name 变量）
#         parent    —— 模型继承（parent 段）
#         model     —— blockstates 的 variants/multipart 里的 model 引用
#         other     —— 其它位置出现且不属于上面三类的 opencomputers_neo: 引用
#
# 用法：
#   .\tools\check-models.ps1
#   .\tools\check-models.ps1 -Details      # 额外列出每一个引用（不只列缺失项）
#
# 退出码：0 = 无缺失；1 = 有缺失（可直接用于 CI）。
#
# 说明：只检查本项目命名空间（opencomputers_neo）的引用；minecraft: 与其它命名空间
#       的引用（原版资源）不在本脚本职责范围内（它们由原版资源包提供）。

[CmdletBinding()]
param(
    [string]$Project = (Split-Path -Parent $PSScriptRoot),
    [switch]$Details
)

$ErrorActionPreference = 'Stop'

$assets = Join-Path $Project 'src\main\resources\assets\opencomputers_neo'
$modelsDir = Join-Path $assets 'models'
$blockstatesDir = Join-Path $assets 'blockstates'
$texDir = Join-Path $assets 'textures'
$namespace = 'opencomputers_neo'

if (!(Test-Path $modelsDir)) { throw "找不到模型目录：$modelsDir" }

# --------------------------------------------------------------------------- #
# 1) 收集所有 JSON（模型 + 方块状态）
# --------------------------------------------------------------------------- #
$jsonFiles = @()
$jsonFiles += Get-ChildItem $modelsDir -Recurse -File -Filter '*.json' -ErrorAction SilentlyContinue
if (Test-Path $blockstatesDir) {
    $jsonFiles += Get-ChildItem $blockstatesDir -Recurse -File -Filter '*.json' -ErrorAction SilentlyContinue
}
$jsonFiles = $jsonFiles | Sort-Object FullName

# --------------------------------------------------------------------------- #
# 2) 已有的贴图（相对 textures/ 的路径，小写无扩展名）+ 已有的模型（相对 models/ 的路径）
# --------------------------------------------------------------------------- #
$textureSet = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
Get-ChildItem $texDir -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
    $rel = $_.FullName.Substring($texDir.Length + 1) -replace '\\', '/'
    # 去掉 .png / .png.mcmeta 之类的扩展名
    $rel = $rel -replace '\.png(\.mcmeta)?$', ''
    [void]$textureSet.Add($rel)
}

$modelSet = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
Get-ChildItem $modelsDir -Recurse -File -Filter '*.json' -ErrorAction SilentlyContinue | ForEach-Object {
    $rel = $_.FullName.Substring($modelsDir.Length + 1) -replace '\\', '/'
    $rel = $rel -replace '\.json$', ''
    [void]$modelSet.Add($rel)
}

# --------------------------------------------------------------------------- #
# 3) 逐文件、逐引用检查
# --------------------------------------------------------------------------- #
$results = New-Object System.Collections.Generic.List[object]

function Add-Reference {
    param(
        [string]$SourceFile,
        [string]$Kind,      # textures | parent | model | other
        [string]$Value,     # 原始引用值（可能带 # 前缀或 :namespace）
        [string]$Detail
    )
    $raw = $Value
    $value = $Value
    # 面里的贴图变量（"#all"）先记下来，值由同文件的 textures 段解析；这里只做形式记录。
    $isVariable = $value.StartsWith('#')
    if ($isVariable) { $value = $value.Substring(1) }

    # 只处理本模组命名空间
    if ($value.Contains(':')) {
        $parts = $value.Split(':', 2)
        if ($parts[0] -ne $namespace) { return }
        $path = $parts[1]
    }
    else {
        # 无命名空间：vanilla 模型里 "#var" 之外的相对引用按本模组处理
        if ($isVariable) { return }
        $path = $value
    }

    $exists = $false
    $resolved = ''
    switch ($Kind) {
        'textures' {
            $resolved = "textures/$path"
            $exists = $textureSet.Contains($path)
        }
        'parent' {
            $resolved = "models/$path"
            $exists = $modelSet.Contains($path)
        }
        'model' {
            $resolved = "models/$path"
            $exists = $modelSet.Contains($path)
        }
        default {
            # 未知位置的引用：两种都试一下
            $resolved = "models/$path 或 textures/$path"
            $exists = ($modelSet.Contains($path)) -or ($textureSet.Contains($path))
        }
    }

    $results.Add([pscustomobject]@{
            Source   = $SourceFile
            Kind     = $Kind
            Raw      = $raw
            Resolved = $resolved
            Exists   = $exists
            Detail   = $Detail
        })
}

foreach ($file in $jsonFiles) {
    $rel = $file.FullName.Substring($Project.Length + 1) -replace '\\', '/'
    $text = Get-Content $file.FullName -Raw
    if ([string]::IsNullOrWhiteSpace($text)) {
        $results.Add([pscustomobject]@{
                Source = $rel; Kind = 'other'; Raw = ''; Resolved = ''
                Exists = $false; Detail = 'JSON 文件为空'
            })
        continue
    }

    try { $json = $text | ConvertFrom-Json }
    catch {
        $results.Add([pscustomobject]@{
                Source = $rel; Kind = 'other'; Raw = ''; Resolved = ''
                Exists = $false; Detail = "JSON 解析失败：$($_.Exception.Message)"
            })
        continue
    }

    # ---- parent ----
    if ($json.PSObject.Properties.Name -contains 'parent') {
        if ($json.parent) { Add-Reference -SourceFile $rel -Kind 'parent' -Value ([string]$json.parent) -Detail 'parent' }
    }

    # ---- textures ----
    if ($json.PSObject.Properties.Name -contains 'textures' -and $json.textures) {
        foreach ($prop in $json.textures.PSObject.Properties) {
            if ($prop.Value) {
                Add-Reference -SourceFile $rel -Kind 'textures' -Value ([string]$prop.Value) -Detail "textures.$($prop.Name)"
            }
        }
    }

    # ---- blockstates: variants / multipart 里的 model ----
    if ($json.PSObject.Properties.Name -contains 'variants' -and $json.variants) {
        foreach ($variant in $json.variants.PSObject.Properties) {
            $entries = @($variant.Value)
            foreach ($entry in $entries) {
                if ($null -eq $entry) { continue }
                # 单个对象或数组
                $models = @()
                if ($entry.PSObject.Properties.Name -contains 'model') { $models += $entry.model }
                foreach ($m in $models) {
                    if ($m) { Add-Reference -SourceFile $rel -Kind 'model' -Value ([string]$m) -Detail "variants.$($variant.Name)" }
                }
            }
        }
    }
    if ($json.PSObject.Properties.Name -contains 'multipart' -and $json.multipart) {
        foreach ($part in @($json.multipart)) {
            if ($null -ne $part -and ($part.PSObject.Properties.Name -contains 'apply')) {
                foreach ($apply in @($part.apply)) {
                    if ($null -ne $apply -and ($apply.PSObject.Properties.Name -contains 'model') -and $apply.model) {
                        Add-Reference -SourceFile $rel -Kind 'model' -Value ([string]$apply.model) -Detail 'multipart.apply'
                    }
                }
            }
        }
    }

    # ---- elements[].faces[].texture（"#var" 变量；仅记录，不判定存在性）----
    if ($Details -and $json.PSObject.Properties.Name -contains 'elements' -and $json.elements) {
        foreach ($element in @($json.elements)) {
            if ($null -eq $element -or -not ($element.PSObject.Properties.Name -contains 'faces')) { continue }
            foreach ($face in $element.faces.PSObject.Properties) {
                $faceValue = $face.Value
                if ($null -ne $faceValue -and ($faceValue.PSObject.Properties.Name -contains 'texture') -and $faceValue.texture) {
                    Add-Reference -SourceFile $rel -Kind 'textures' -Value ([string]$faceValue.texture) -Detail "elements[].faces.$($face.Name)"
                }
            }
        }
    }
}

# --------------------------------------------------------------------------- #
# 4) 报告
# --------------------------------------------------------------------------- #
$missing = @($results | Where-Object { -not $_.Exists })
$byKind = $results | Group-Object Kind | Sort-Object Name

Write-Output "扫描文件：$($jsonFiles.Count) 个 JSON；引用总数：$($results.Count)"
foreach ($g in $byKind) {
    $bad = @($g.Group | Where-Object { -not $_.Exists }).Count
    Write-Output ("  {0,-9} 引用 {1,4} 个，缺失 {2} 个" -f $g.Name, $g.Count, $bad)
}

if ($Details) {
    Write-Output ''
    Write-Output '--- 全部引用 ---'
    $results | Sort-Object Kind, Source, Raw | Format-Table -AutoSize |
        Out-String -Width 240 | Write-Output
}

Write-Output ''
Write-Output '--- 缺失引用 ---'
if ($missing.Count -eq 0) {
    Write-Output '(无)'
}
else {
    $missing | Sort-Object Kind, Source, Raw |
        Select-Object @{n = 'Kind'; e = { $_.Kind } },
                      @{n = 'Source'; e = { $_.Source } },
                      @{n = 'Reference'; e = { $_.Raw } },
                      @{n = 'Expected'; e = { $_.Resolved } },
                      @{n = 'Where'; e = { $_.Detail } } |
        Format-Table -AutoSize | Out-String -Width 240 | Write-Output
}

if ($missing.Count -gt 0) { exit 1 }
exit 0
