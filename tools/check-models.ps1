# 模型引用兜底校验（OpenComputers Neo / 1.21.1）
#
# 作用：扫描 assets/opencomputers_neo 下的 models 与 blockstates，把其中出现的
#       "opencomputers_neo:xxx" 引用逐个解析成真实文件路径并检查是否存在，
#       按引用类型分类报告：
#         textures  —— 贴图引用（textures 段里的值；也含面里 "#name" 变量解析后的结果）
#         parent    —— 模型继承（parent 段）
#         model     —— blockstates 的 variants / multipart 里的 model 引用
#         other     —— JSON 解析失败、空文件等结构性问题的占位条目
#
# 用法：
#   .\tools\check-models.ps1
#   .\tools\check-models.ps1 -Details      # 额外列出每一个引用（不只列缺失项）
#
# 退出码：0 = 无缺失；1 = 有缺失（可直接用于 CI）。
#
# 说明：
#  - 只检查本项目命名空间（opencomputers_neo）；minecraft: 等原版命名空间的引用由原版资源包提供，
#    不在本脚本职责范围内（若该模型有 elements，本脚本仍会解析其中的 #变量）。
#  - 解析用 JavaScriptSerializer 而不是 ConvertFrom-Json：Windows PowerShell 5.1 的
#    ConvertFrom-Json 无法处理 blockstate 里 `"": { ... }` 这种**空字符串键**，会直接抛异常。

[CmdletBinding()]
param(
    [string]$Project = (Split-Path -Parent $PSScriptRoot),
    [switch]$Details
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Web.Extensions | Out-Null

$assets = Join-Path $Project 'src\main\resources\assets\opencomputers_neo'
$modelsDir = Join-Path $assets 'models'
$blockstatesDir = Join-Path $assets 'blockstates'
$texDir = Join-Path $assets 'textures'
$namespace = 'opencomputers_neo'

if (!(Test-Path $modelsDir)) { throw "找不到模型目录：$modelsDir" }

# --------------------------------------------------------------------------- #
# 0) JSON 解析器
# --------------------------------------------------------------------------- #
$serializer = New-Object System.Web.Script.Serialization.JavaScriptSerializer
$serializer.MaxJsonLength = 64MB

function ConvertTo-Data {
    param([string]$Text)
    return $serializer.DeserializeObject($Text)
}

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
# 2) 已有的贴图（相对 textures/ 的路径）+ 已有的模型（相对 models/ 的路径）
# --------------------------------------------------------------------------- #
$textureSet = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
Get-ChildItem $texDir -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
    $rel = $_.FullName.Substring($texDir.Length + 1) -replace '\\', '/'
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
# 3) 引用检查
# --------------------------------------------------------------------------- #
$results = New-Object System.Collections.Generic.List[object]

# 相对路径 -> 已解析的 JSON（供下面的「贴图变量解析」一节沿 parent 链查询）
$parsedModels = @{}

function Add-Result {
    param([string]$Source, [string]$Kind, [string]$Raw, [string]$Resolved, [bool]$Exists, [string]$Where)
    $results.Add([pscustomobject]@{
            Kind     = $Kind
            Source   = $Source
            Raw      = $Raw
            Resolved = $Resolved
            Exists   = $Exists
            Where    = $Where
        })
}

# 检查一个 opencomputers_neo: 引用（textures / parent / model / other）
function Test-Reference {
    param([string]$Source, [string]$Kind, [string]$Value, [string]$Where)

    if ([string]::IsNullOrWhiteSpace($Value)) { return }
    $value = $Value.Trim()

    # 贴图变量（"#all"）由调用方先解析；这里再遇到说明解析失败，直接记缺失。
    if ($value.StartsWith('#')) {
        Add-Result -Source $Source -Kind $Kind -Raw $Value -Resolved '(未解析的贴图变量)' -Exists $false -Where $Where
        return
    }

    if ($value.Contains(':')) {
        $parts = $value.Split(':', 2)
        if ($parts[0] -ne $namespace) { return }   # 其它命名空间不归本脚本管
        $path = $parts[1]
    }
    else {
        $path = $value
    }

    switch ($Kind) {
        'textures' {
            Add-Result -Source $Source -Kind $Kind -Raw $Value -Resolved "textures/$path" `
                -Exists $textureSet.Contains($path) -Where $Where
        }
        'parent' {
            Add-Result -Source $Source -Kind $Kind -Raw $Value -Resolved "models/$path" `
                -Exists $modelSet.Contains($path) -Where $Where
        }
        'model' {
            Add-Result -Source $Source -Kind $Kind -Raw $Value -Resolved "models/$path" `
                -Exists $modelSet.Contains($path) -Where $Where
        }
        default {
            Add-Result -Source $Source -Kind $Kind -Raw $Value -Resolved "models/$path 或 textures/$path" `
                -Exists ($modelSet.Contains($path) -or $textureSet.Contains($path)) -Where $Where
        }
    }
}

# 解析 "#var"：在本文件的 textures 段里查 var 的值，再按贴图引用检查。
# 本文件没定义该变量时**不算缺失**：模型的 textures 是沿 parent 链合并的，
# 变量常常由使用该模板的子模型定义（例如 block/tinted_cube.json 的 #down）。
# 变量是否最终有定义，由脚本末尾的「贴图变量解析」一节单独检查。
function Test-TextureVariable {
    param([string]$Source, [string]$Variable, $Textures, [string]$Where)

    $name = $Variable.Substring(1)
    if ($null -eq $Textures -or -not $Textures.ContainsKey($name)) {
        Add-Result -Source $Source -Kind 'textures' -Raw $Variable -Resolved '(由 parent 链 / 子模型提供)' `
            -Exists $true -Where $Where
        return
    }
    Test-Reference -Source $Source -Kind 'textures' -Value ([string]$Textures[$name]) -Where "$Where (via #$name)"
}

foreach ($file in $jsonFiles) {
    $rel = $file.FullName.Substring($Project.Length + 1) -replace '\\', '/'
    $text = Get-Content $file.FullName -Raw
    if ([string]::IsNullOrWhiteSpace($text)) {
        Add-Result -Source $rel -Kind 'other' -Raw '' -Resolved '' -Exists $false -Where 'JSON 文件为空'
        continue
    }

    try { $json = ConvertTo-Data -Text $text }
    catch {
        Add-Result -Source $rel -Kind 'other' -Raw '' -Resolved '' -Exists $false -Where "JSON 解析失败：$($_.Exception.Message)"
        continue
    }

    if ($null -eq $json -or -not ($json -is [System.Collections.IDictionary])) {
        Add-Result -Source $rel -Kind 'other' -Raw '' -Resolved '' -Exists $false -Where 'JSON 顶层不是对象'
        continue
    }

    $parsedModels[$rel] = $json

    $textures = if ($json.ContainsKey('textures')) { $json['textures'] } else { $null }

    # ---- parent ----
    if ($json.ContainsKey('parent') -and $json['parent']) {
        Test-Reference -Source $rel -Kind 'parent' -Value ([string]$json['parent']) -Where 'parent'
    }

    # ---- textures 段 ----
    if ($null -ne $textures -and ($textures -is [System.Collections.IDictionary])) {
        foreach ($key in @($textures.Keys)) {
            $value = $textures[$key]
            if ($null -eq $value) { continue }
            $value = [string]$value
            if ($value.StartsWith('#')) {
                # 贴图变量再指向另一个变量：只在 Details 下记录，不作为缺失
                if ($Details) {
                    Add-Result -Source $rel -Kind 'textures' -Raw $value -Resolved '(变量引用)' -Exists $true `
                        -Where "textures.$key"
                }
            }
            else {
                Test-Reference -Source $rel -Kind 'textures' -Value $value -Where "textures.$key"
            }
        }
    }

    # ---- elements[].faces[].texture ----
    if ($json.ContainsKey('elements') -and $json['elements']) {
        $elements = @($json['elements'])
        for ($i = 0; $i -lt $elements.Count; $i++) {
            $element = $elements[$i]
            if ($null -eq $element -or -not ($element -is [System.Collections.IDictionary])) { continue }
            if (-not $element.ContainsKey('faces') -or $null -eq $element['faces']) { continue }
            $faces = $element['faces']
            foreach ($faceName in @($faces.Keys)) {
                $face = $faces[$faceName]
                if ($null -eq $face -or -not ($face -is [System.Collections.IDictionary])) { continue }
                if (-not $face.ContainsKey('texture') -or $null -eq $face['texture']) { continue }
                $faceTexture = [string]$face['texture']
                $where = "elements[$i].faces.$faceName"
                if ($faceTexture.StartsWith('#')) {
                    Test-TextureVariable -Source $rel -Variable $faceTexture -Textures $textures -Where $where
                }
                elseif ($Details) {
                    Test-Reference -Source $rel -Kind 'textures' -Value $faceTexture -Where $where
                }
            }
        }
    }

    # ---- blockstates: variants ----
    if ($json.ContainsKey('variants') -and $json['variants']) {
        $variants = $json['variants']
        foreach ($variantName in @($variants.Keys)) {
            $value = $variants[$variantName]
            if ($null -eq $value) { continue }
            # 单个对象或对象数组
            $entries = @()
            if ($value -is [System.Collections.IDictionary]) { $entries = @($value) }
            elseif ($value -is [System.Collections.IEnumerable] -and -not ($value -is [string])) { $entries = @($value) }
            foreach ($entry in $entries) {
                if ($null -eq $entry -or -not ($entry -is [System.Collections.IDictionary])) { continue }
                if ($entry.ContainsKey('model') -and $entry['model']) {
                    Test-Reference -Source $rel -Kind 'model' -Value ([string]$entry['model']) `
                        -Where "variants.$variantName"
                }
            }
        }
    }

    # ---- blockstates: multipart ----
    if ($json.ContainsKey('multipart') -and $json['multipart']) {
        $partIndex = 0
        foreach ($part in @($json['multipart'])) {
            $partIndex++
            if ($null -eq $part -or -not ($part -is [System.Collections.IDictionary])) { continue }
            if (-not $part.ContainsKey('apply') -or $null -eq $part['apply']) { continue }
            $applyValue = $part['apply']
            $applies = @()
            if ($applyValue -is [System.Collections.IDictionary]) { $applies = @($applyValue) }
            elseif ($applyValue -is [System.Collections.IEnumerable] -and -not ($applyValue -is [string])) { $applies = @($applyValue) }
            foreach ($apply in $applies) {
                if ($null -eq $apply -or -not ($apply -is [System.Collections.IDictionary])) { continue }
                if ($apply.ContainsKey('model') -and $apply['model']) {
                    Test-Reference -Source $rel -Kind 'model' -Value ([string]$apply['model']) `
                        -Where "multipart[$partIndex].apply"
                }
            }
        }
    }
}

# --------------------------------------------------------------------------- #
# 4) 贴图变量解析（#name 是否沿 parent 链最终指向一张真实贴图）
#
#    这一步是「变量是否根本没定义」的兜底：缺失的 #变量 会让 MC 自身在烘焙模型时直接报错，
#    本脚本提前把它找出来，并把最终指向的 png 也一并检查一次。
#    只处理本模组自己的模型（原版模型的 #变量由原版资源包保证）。
# --------------------------------------------------------------------------- #
$varProblems = New-Object System.Collections.Generic.List[object]

# 沿 parent 链解析一个贴图变量；返回 @{ Path = 最终贴图路径（或 $null）; Missing = 定义缺失的变量名数组 }
function Resolve-TextureVariable {
    param([string]$StartPath, [string]$Name)

    $visited = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
    $current = $StartPath
    $missing = New-Object System.Collections.Generic.List[string]

    while ($true) {
        if ([string]::IsNullOrEmpty($current) -or -not $visited.Add($current)) { break }
        if (-not $parsedModels.ContainsKey($current)) { break }
        $model = $parsedModels[$current]
        if ($model.ContainsKey('textures') -and $model['textures'] -is [System.Collections.IDictionary]) {
            $textures = $model['textures']
            if ($textures.ContainsKey($Name)) {
                $value = [string]$textures[$Name]
                if ($value.StartsWith('#')) { $Name = $value.Substring(1); $current = $StartPath; continue }
                return @{ Path = $value; Missing = $missing }
            }
        }
        # 继续向上找 parent
        if ($model.ContainsKey('parent') -and $model['parent']) {
            $parent = [string]$model['parent']
            if (-not $parent.Contains(':')) { $parent = "$namespace`:$parent" }
            if (-not $parent.StartsWith("$namespace`:")) { break }   # 到了原版模型，交给原版
            $current = $parent
            continue
        }
        break
    }

    $missing.Add($Name) | Out-Null
    return @{ Path = $null; Missing = $missing }
}

foreach ($rel in ($parsedModels.Keys | Sort-Object)) {
    $json = $parsedModels[$rel]

    $usedVariables = New-Object System.Collections.Generic.HashSet[string]
    if ($json.ContainsKey('elements') -and $json['elements']) {
        foreach ($element in @($json['elements'])) {
            if ($null -eq $element -or -not ($element -is [System.Collections.IDictionary])) { continue }
            if (-not $element.ContainsKey('faces') -or $null -eq $element['faces']) { continue }
            foreach ($faceName in @($element['faces'].Keys)) {
                $face = $element['faces'][$faceName]
                if ($null -eq $face -or -not ($face -is [System.Collections.IDictionary])) { continue }
                if ($face.ContainsKey('texture') -and $face['texture']) {
                    $faceTexture = [string]$face['texture']
                    if ($faceTexture.StartsWith('#')) { [void]$usedVariables.Add($faceTexture.Substring(1)) }
                }
            }
        }
    }
    if ($json.ContainsKey('textures') -and $json['textures'] -is [System.Collections.IDictionary]) {
        foreach ($key in @($json['textures'].Keys)) {
            $value = $json['textures'][$key]
            if ($null -ne $value -and ([string]$value).StartsWith('#')) {
                [void]$usedVariables.Add(([string]$value).Substring(1))
            }
        }
    }

    foreach ($name in $usedVariables) {
        $resolved = Resolve-TextureVariable -StartPath $rel -Name $name
        if ($null -eq $resolved.Path) {
            $varProblems.Add([pscustomobject]@{
                    Source    = $rel
                    Variable  = "#$name"
                    Resolved  = '(沿 parent 链未找到定义)'
                    TextureOk = $false
                    Where     = '贴图变量'
                })
        }
        else {
            $textureOk = Test-ReferenceOnce -Value $resolved.Path
            if (-not $textureOk) {
                $varProblems.Add([pscustomobject]@{
                        Source    = $rel
                        Variable  = "#$name"
                        Resolved  = $resolved.Path
                        TextureOk = $false
                        Where     = '贴图变量'
                    })
            }
        }
    }
}

# 只判定本模组命名空间的引用是否存在（不写进 $results，避免重复计数）
function Test-ReferenceOnce {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return $true }
    $value = $Value.Trim()
    if ($value.StartsWith('#')) { return $true }
    if ($value.Contains(':')) {
        $parts = $value.Split(':', 2)
        if ($parts[0] -ne $namespace) { return $true }
        $path = $parts[1]
    }
    else { $path = $value }
    return $textureSet.Contains($path)
}

# --------------------------------------------------------------------------- #
# 5) 报告
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
    $results | Sort-Object Kind, Source, Where |
        Format-Table -AutoSize | Out-String -Width 240 | Write-Output
}

Write-Output ''
Write-Output '--- 缺失引用 ---'
if ($missing.Count -eq 0) {
    Write-Output '(无)'
}
else {
    $missing | Sort-Object Kind, Source, Where |
        Select-Object @{n = 'Kind'; e = { $_.Kind } },
                      @{n = 'Source'; e = { $_.Source } },
                      @{n = 'Reference'; e = { $_.Raw } },
                      @{n = 'Expected'; e = { $_.Resolved } },
                      @{n = 'Where'; e = { $_.Where } } |
        Format-Table -AutoSize | Out-String -Width 240 | Write-Output
}

if ($missing.Count -gt 0) { exit 1 }
exit 0
