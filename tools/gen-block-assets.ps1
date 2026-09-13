# 为 OpenComputers Neo 生成 1.21.1 方块资源（blockstates / models）。
#
# 背景：1.7.10 用 IIcon 在代码里指定每个面的贴图（`customTextures`，面序为
# ForgeDirection 序：DOWN, UP, NORTH, SOUTH, WEST, EAST）；1.21.1 已无图标系统，
# 改为烘焙模型。本脚本把原 `customTextures` 的语义落成：
#   assets/opencomputers_neo/blockstates/<block>.json
#   assets/opencomputers_neo/models/block/<block>.json
#   assets/opencomputers_neo/models/item/<block>.json
#
# 注意：1.21.1 的 ResourceLocation 只允许 [a-z0-9/._-]，因此贴图文件名必须全小写，
# 本脚本会先把 textures/block 下的文件重命名为小写。
param(
    [string]$Project = 'D:\Workspace\Mods\1.21.1\OpenComputers Neo'
)

$ErrorActionPreference = 'Stop'
$assets = Join-Path $Project 'src\main\resources\assets\opencomputers_neo'
$texDir = Join-Path $assets 'textures\block'
$blockDir = Join-Path $assets 'blockstates'
$modelDir = Join-Path $assets 'models\block'
$itemDir = Join-Path $assets 'models\item'
$lootDir = Join-Path $Project 'src\main\resources\data\opencomputers_neo\loot_table\blocks'
foreach ($d in @($blockDir, $modelDir, $itemDir, $lootDir)) {
    if (!(Test-Path $d)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }
}

# --------------------------------------------------------------------------- #
# 1) 贴图文件名转小写（含 .mcmeta）
# --------------------------------------------------------------------------- #
Get-ChildItem $texDir -File | ForEach-Object {
    $lower = $_.Name.ToLowerInvariant()
    # 注意：PowerShell 的字符串比较默认不区分大小写，这里必须用 -cne，
    # 否则「只改大小写」的重命名会被判定为「无需修改」而跳过。
    if ($lower -cne $_.Name) {
        $source = $_.FullName
        $target = Join-Path $texDir $lower
        # 目标与源只是大小写不同（Windows 下 Test-Path / File.Exists 不区分大小写），
        # 因此只有两者确实是不同文件时才删除目标，纯大小写重命名交给 File.Move。
        if (-not $target.Equals($source, [System.StringComparison]::OrdinalIgnoreCase) -and
            [System.IO.File]::Exists($target)) {
            [System.IO.File]::Delete($target)
        }
        [System.IO.File]::Move($source, $target)
    }
}

# --------------------------------------------------------------------------- #
# 2) 方块 → 六面贴图（DOWN, UP, NORTH, SOUTH, WEST, EAST）
#    'generictop' / 'genericside' 是原 SimpleBlock 的默认面（None 分支）。
#    空数组 = 无模型（屏幕 / 机器人等由方块实体渲染器负责）。
# --------------------------------------------------------------------------- #
$oc = 'opencomputers_neo:block/'
$default = @('generictop', 'generictop', 'genericside', 'genericside', 'genericside', 'genericside')

$blocks = [ordered]@{
    # 名称            DOWN          UP                    NORTH              SOUTH              WEST               EAST
    'accesspoint'     = @('generictop', 'accesspointtop', 'switchside', 'switchside', 'switchside', 'switchside')
    'adapter'         = @('adaptertop', 'adaptertop', 'adapterside', 'adapterside', 'adapterside', 'adapterside')
    'assembler'       = @('generictop', 'assemblertop', 'assemblerside', 'assemblerside', 'assemblerside', 'assemblerside')
    'cable'           = @('cablepart', 'cablepart', 'cablepart', 'cablepart', 'cablepart', 'cablepart')
    'capacitor'       = @('generictop', 'capacitortop', 'capacitorside', 'capacitorside', 'capacitorside', 'capacitorside')
    'carpetedcapacitor' = @('generictop', 'carpetcapacitortop', 'capacitorside', 'capacitorside', 'capacitorside', 'capacitorside')
    'casecreative'    = @('casetop', 'casetop', 'caseback', 'casefront', 'caseside', 'caseside')
    'case1'           = @('casetop', 'casetop', 'caseback', 'casefront', 'caseside', 'caseside')
    'case2'           = @('casetop', 'casetop', 'caseback', 'casefront', 'caseside', 'caseside')
    'case3'           = @('casetop', 'casetop', 'caseback', 'casefront', 'caseside', 'caseside')
    'chameliumblock'  = @('white', 'white', 'white', 'white', 'white', 'white')
    'charger'         = @('generictop', 'generictop', 'chargerside', 'chargerfront', 'chargerside', 'chargerside')
    'disassembler'    = @('generictop', 'disassemblertop', 'disassemblerside', 'disassemblerside', 'disassemblerside', 'disassemblerside')
    'diskdrive'       = @('generictop', 'generictop', 'diskdriveside', 'diskdrivefront', 'diskdriveside', 'diskdriveside')
    'endstone'        = @('minecraft:block/end_stone', 'minecraft:block/end_stone', 'minecraft:block/end_stone', 'minecraft:block/end_stone', 'minecraft:block/end_stone', 'minecraft:block/end_stone')
    'geolyzer'        = @('generictop', 'geolyzertop', 'geolyzerside', 'geolyzerside', 'geolyzerside', 'geolyzerside')
    'hologram1'       = @('generictop', 'hologramtop0', 'hologramside', 'hologramside', 'hologramside', 'hologramside')
    'hologram2'       = @('generictop', 'hologramtop1', 'hologramside', 'hologramside', 'hologramside', 'hologramside')
    'keyboard'        = @('keyboard', 'keyboard', 'keyboard', 'keyboard', 'keyboard', 'keyboard')
    'microcontroller' = @('microcontrollertop', 'microcontrollertop', 'microcontrollerside', 'microcontrollerfront', 'microcontrollerside', 'microcontrollerside')
    'motionsensor'    = @('motionsensortop', 'motionsensortop', 'motionsensorside', 'motionsensorside', 'motionsensorside', 'motionsensorside')
    'netsplitter'     = @('netsplittertop', 'netsplittertop', 'netsplitterside', 'netsplitterside', 'netsplitterside', 'netsplitterside')
    'powerconverter'  = @('generictop', 'generictop', 'powerconverterside', 'powerconverterside', 'powerconverterside', 'powerconverterside')
    'powerdistributor' = @('generictop', 'powerdistributortop', 'powerdistributorside', 'powerdistributorside', 'powerdistributorside', 'powerdistributorside')
    'print'           = @('generictop', 'generictop', 'genericside', 'genericside', 'genericside', 'genericside')
    'printer'         = @('generictop', 'printertop', 'printerside', 'printerside', 'printerside', 'printerside')
    'rack'            = @('generictop', 'generictop', 'rackside', 'rackfront', 'rackside', 'rackside')
    'raid'            = @('generictop', 'generictop', 'raidside', 'raidfront', 'raidside', 'raidside')
    'redstone'        = @('redstonebottom', 'redstonetop', 'redstonenorth', 'redstonesouth', 'redstonewest', 'redstoneeast')
    'relay'           = @('generictop', 'switchtop', 'switchside', 'switchside', 'switchside', 'switchside')
    'robot'           = @()
    'robotafterimage' = @()
    'screen1'         = @()
    'screen2'         = @()
    'screen3'         = @()
    'rackmountable'   = @()
    'switch'          = @('generictop', 'switchtop', 'switchside', 'switchside', 'switchside', 'switchside')
    'transposer'      = @('transposertop', 'transposertop', 'transposerside', 'transposerside', 'transposerside', 'transposerside')
    'waypoint'        = @('generictop', 'waypointtop', 'waypointback', 'waypointfront', 'waypointside', 'waypointside')
}

$faces = @('down', 'up', 'north', 'south', 'west', 'east')
$utf8 = New-Object System.Text.UTF8Encoding($false)
$count = 0

foreach ($name in $blocks.Keys) {
    $textures = $blocks[$name]
    $json = $null
    if ($textures.Count -eq 0) {
        # 无模型：屏幕 / 机器人 / 残影等完全由方块实体渲染器绘制（客户端阶段接入）。
        $json = [ordered]@{
            textures = [ordered]@{ particle = ($oc + 'genericside') }
        }
    }
    else {
        $particle = if ($textures[0] -like '*:*') { $textures[0] } else { $oc + $textures[0] }
        $tex = [ordered]@{ particle = $particle }
        for ($i = 0; $i -lt 6; $i++) {
            $tex[$faces[$i]] = if ($textures[$i] -like '*:*') { $textures[$i] } else { $oc + $textures[$i] }
        }
        $json = [ordered]@{
            parent   = 'minecraft:block/cube'
            textures = $tex
        }
    }

    $model = [ordered]@{
        variants = [ordered]@{
            '' = [ordered]@{ model = ($oc + $name) }
        }
    }

    $item = [ordered]@{ parent = ($oc + $name) }

    # 战利品表：1.21.1 的方块掉落完全走战利品表，路径为
    # data/opencomputers_neo/loot_table/blocks/<方块注册名>.json（1.21 起目录名是单数 loot_table）。
    $loot = [ordered]@{
        'type'  = 'minecraft:block'
        pools = @(
            [ordered]@{
                rolls    = 1
                bonus_rolls = 0
                entries  = @(
                    [ordered]@{
                        type   = 'minecraft:item'
                        name   = ('opencomputers_neo:' + $name)
                        weight = 1
                    }
                )
                conditions = @(
                    [ordered]@{ condition = 'minecraft:survives_explosion' }
                )
            }
        )
        random_sequence = ('opencomputers_neo:blocks/' + $name)
    }

    [System.IO.File]::WriteAllText((Join-Path $modelDir "$name.json"), ($json | ConvertTo-Json -Depth 8), $utf8)
    [System.IO.File]::WriteAllText((Join-Path $blockDir "$name.json"), ($model | ConvertTo-Json -Depth 8), $utf8)
    [System.IO.File]::WriteAllText((Join-Path $itemDir "$name.json"), ($item | ConvertTo-Json -Depth 8), $utf8)
    [System.IO.File]::WriteAllText((Join-Path $lootDir "$name.json"), ($loot | ConvertTo-Json -Depth 8), $utf8)
    $count++
}

Write-Output ("generated assets for {0} blocks (blockstates + models/block + models/item + loot_table)" -f $count)
