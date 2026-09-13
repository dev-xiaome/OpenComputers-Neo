# 为 OpenComputers Neo 的物品批量生成 1.21.1 物品模型 JSON。
#
# 用法：
#   .\tools\gen-item-models.ps1
#
# 说明：
#  - 1.7.10 的贴图在 assets/<ns>/textures/items/<UnlocalizedName>.png，
#    迁移后目录改名为 item/，文件名不变，因此这里按「类名 + 等级后缀」推导贴图名。
#  - 每个物品（Constants.ItemName.* 常量）生成一个
#    assets/<ns>/models/item/<常量名>.json，内容统一为：
#      {"parent":"minecraft:item/generated","textures":{"layer0":"<ns>:item/<贴图名>"}}
#  - 带 .png.mcmeta 的动画贴图无需特殊处理。
#  - 本脚本只增删自己生成的 <常量名>.json（已存在但内容相同的文件不会重写）。

param(
    [string]$Project = 'D:\Workspace\Mods\1.21.1\OpenComputers Neo'
)

$ErrorActionPreference = 'Stop'

$ns = 'opencomputers_neo'
$constantsPath = Join-Path $Project 'src\main\scala\li\cil\oc\Constants.scala'
$modelDir = Join-Path $Project "src\main\resources\assets\$ns\models\item"
$textureDir = Join-Path $Project "src\main\resources\assets\$ns\textures\item"

if (-not (Test-Path $constantsPath)) { throw "Cannot find Constants.scala: $constantsPath" }
if (-not (Test-Path $textureDir)) { throw "Cannot find texture dir: $textureDir" }
New-Item -ItemType Directory -Force -Path $modelDir | Out-Null

# 物品注册名 -> 贴图名（不含 .png）。贴图名一般就是 "类名 + 等级后缀"。
# 这里显式列出，保证与 1.7.10 的 setTextureName 完全一致。
$textures = [ordered]@{
    'abstractBusCard'          = 'AbstractBusCard'
    'acid'                     = 'Acid'
    'alu'                      = 'ALU'
    'analyzer'                 = 'Analyzer'
    'angelUpgrade'             = 'UpgradeAngel'
    'apuCreative'              = 'APU2'
    'apu1'                     = 'APU0'
    'apu2'                     = 'APU1'
    'arrowKeys'                = 'ArrowKeys'
    'batteryUpgrade1'          = 'UpgradeBattery0'
    'batteryUpgrade2'          = 'UpgradeBattery1'
    'batteryUpgrade3'          = 'UpgradeBattery2'
    'buttonGroup'              = 'ButtonGroup'
    'card'                     = 'CardBase'
    'cardContainer1'           = 'UpgradeContainerCard0'
    'cardContainer2'           = 'UpgradeContainerCard1'
    'cardContainer3'           = 'UpgradeContainerCard2'
    'chamelium'                = 'Chamelium'
    'chip1'                    = 'Microchip0'
    'chip2'                    = 'Microchip1'
    'chip3'                    = 'Microchip2'
    'chunkloaderUpgrade'       = 'UpgradeChunkloader'
    'circuitBoard'             = 'CircuitBoard'
    'componentBus1'            = 'ComponentBus0'
    'componentBus2'            = 'ComponentBus1'
    'componentBus3'            = 'ComponentBus2'
    'componentBusCreative'     = 'ComponentBus3'
    'cpu1'                     = 'CPU0'
    'cpu2'                     = 'CPU1'
    'cpu3'                     = 'CPU2'
    'craftingUpgrade'          = 'UpgradeCrafting'
    'cu'                       = 'ControlUnit'
    'cuttingWire'              = 'CuttingWire'
    'databaseUpgrade1'         = 'UpgradeDatabase0'
    'databaseUpgrade2'         = 'UpgradeDatabase1'
    'databaseUpgrade3'         = 'UpgradeDatabase2'
    'dataCard1'                = 'DataCard0'
    'dataCard2'                = 'DataCard1'
    'dataCard3'                = 'DataCard2'
    'debugCard'                = 'DebugCard'
    'debugger'                 = 'Debugger'
    'chipDiamond'              = 'DiamondChip'
    'disk'                     = 'Disk'
    'diskDriveMountable'       = 'DiskDriveMountable'
    'drone'                    = 'Drone'
    'droneCase1'               = 'DroneCase0'
    'droneCase2'               = 'DroneCase1'
    'droneCaseCreative'        = 'DroneCase3'
    'eeprom'                   = 'EEPROM'
    'experienceUpgrade'        = 'UpgradeExperience'
    'floppy'                   = 'FloppyDisk_dyeLightGray'
    'generatorUpgrade'         = 'UpgradeGenerator'
    'graphicsCard1'            = 'GraphicsCard0'
    'graphicsCard2'            = 'GraphicsCard1'
    'graphicsCard3'            = 'GraphicsCard2'
    'hdd1'                     = 'HardDiskDrive0'
    'hdd2'                     = 'HardDiskDrive1'
    'hdd3'                     = 'HardDiskDrive2'
    'hoverBoots'               = 'HoverBoots'
    'hoverUpgrade1'            = 'UpgradeHover0'
    'hoverUpgrade2'            = 'UpgradeHover1'
    'inkCartridgeEmpty'        = 'InkCartridgeEmpty'
    'inkCartridge'             = 'InkCartridge'
    'internetCard'             = 'InternetCard'
    'interweb'                 = 'Interweb'
    'inventoryControllerUpgrade' = 'UpgradeInventoryController'
    'inventoryUpgrade'         = 'UpgradeInventory'
    'nuggetIron'               = 'IronNugget'
    'leashUpgrade'             = 'UpgradeLeash'
    'linkedCard'               = 'LinkedCard'
    'lootDisk'                 = 'FloppyDisk_dyeYellow'
    'luaBios'                  = 'EEPROM'
    'mfu'                      = 'UpgradeMF'
    'manual'                   = 'Manual'
    'microcontrollerCaseCreative' = 'MicrocontrollerCase3'
    'microcontrollerCase1'     = 'MicrocontrollerCase0'
    'microcontrollerCase2'     = 'MicrocontrollerCase1'
    'nanomachines'             = 'Nanomachines'
    'navigationUpgrade'        = 'UpgradeNavigation'
    'lanCard'                  = 'NetworkCard'
    'numPad'                   = 'NumPad'
    'openos'                   = 'FloppyDisk_dyeRed'
    'pistonUpgrade'            = 'UpgradePiston'
    'present'                  = 'Present'
    'printedCircuitBoard'      = 'PrintedCircuitBoard'
    'ram1'                     = 'Memory0'
    'ram2'                     = 'Memory1'
    'ram3'                     = 'Memory2'
    'ram4'                     = 'Memory3'
    'ram5'                     = 'Memory4'
    'ram6'                     = 'Memory5'
    'rawCircuitBoard'          = 'RawCircuitBoard'
    'redstoneCard1'            = 'RedstoneCard0'
    'redstoneCard2'            = 'RedstoneCard1'
    'serverCreative'           = 'Server3'
    'server1'                  = 'Server0'
    'server2'                  = 'Server1'
    'server3'                  = 'Server2'
    'signUpgrade'              = 'UpgradeSign'
    'solarGeneratorUpgrade'    = 'UpgradeSolarGenerator'
    'tablet'                   = 'Tablet'
    'tabletCaseCreative'       = 'TabletCase3'
    'tabletCase1'              = 'TabletCase0'
    'tabletCase2'              = 'TabletCase1'
    'tankControllerUpgrade'    = 'UpgradeTankController'
    'tankUpgrade'              = 'UpgradeTank'
    'terminal'                 = 'Terminal'
    'terminalServer'           = 'TerminalServer'
    'texturePicker'            = 'TexturePicker'
    'tractorBeamUpgrade'       = 'UpgradeTractorBeam'
    'tradingUpgrade'           = 'UpgradeTrading'
    'transistor'               = 'Transistor'
    'upgradeContainer1'        = 'UpgradeContainerUpgrade0'
    'upgradeContainer2'        = 'UpgradeContainerUpgrade1'
    'upgradeContainer3'        = 'UpgradeContainerUpgrade2'
    'wlanCard1'                = 'WirelessNetworkCard0'
    'wlanCard2'                = 'WirelessNetworkCard1'
    'worldSensorCard'          = 'WorldSensorCard'
    'wrench'                   = 'Wrench'
}

# 1) 校验贴图存在（含 .mcmeta 的动画贴图同样按 .png 判断）。
$missing = New-Object System.Collections.Generic.List[string]
foreach ($entry in $textures.GetEnumerator()) {
    $png = Join-Path $textureDir ($entry.Value + '.png')
    if (-not (Test-Path $png)) { $missing.Add("$($entry.Key) -> $($entry.Value).png") }
}
if ($missing.Count -gt 0) {
    Write-Output "WARNING: missing textures ($($missing.Count)):"
    $missing | ForEach-Object { Write-Output "  $_" }
}

# 2) 校验注册名与 Constants.ItemName 的一致性。
$declared = @{}
Select-String -Path $constantsPath -Pattern 'final val \w+ = "([A-Za-z0-9_]+)"' -AllMatches | ForEach-Object {
    foreach ($m in $_.Matches) { $declared[$m.Groups[1].Value] = $true }
}
# `Constants.ItemName.*` 里以 def 计算出来的 case 名（droneCase1 等）也在此表中。
foreach ($name in $textures.Keys) {
    if (-not $declared.ContainsKey($name)) {
        Write-Output "WARNING: '$name' is not a Constants.ItemName/BlockName constant"
    }
}

# 3) 生成模型。
#
# 注意文件名必须与**注册名**一致：1.21.1 的注册名只允许小写
# （见 `common/init/Registry.scala#registryName`），
# `ItemModelShaper` 查找的是 `assets/<ns>/models/item/<注册名>.json`，
# 所以即便 `Constants.ItemName` 里是 `dataCard1`，模型文件也必须叫 `datacard1.json`。
$written = 0
$unchanged = 0
$stale = New-Object System.Collections.Generic.List[string]
foreach ($entry in $textures.GetEnumerator()) {
    $json = '{"parent":"minecraft:item/generated","textures":{"layer0":"' + $ns + ':item/' + $entry.Value + '"}}'
    $name = $entry.Key.ToLowerInvariant()
    if ($name -ne $entry.Key) { $stale.Add($entry.Key + '.json') }
    $path = Join-Path $modelDir ($name + '.json')
    if ((Test-Path $path) -and ([System.IO.File]::ReadAllText($path).Trim() -eq $json)) {
        $unchanged++
        continue
    }
    [System.IO.File]::WriteAllText($path, $json + "`n", (New-Object System.Text.UTF8Encoding($false)))
    $written++
}

# 4) 清理大小写不匹配的历史文件（只会删掉本脚本自己生成的 `<常量名>.json`）。
foreach ($staleName in $stale) {
    $stalePath = Join-Path $modelDir $staleName
    if (Test-Path $stalePath) {
        Remove-Item $stalePath -Force
        Write-Output "removed stale model: $staleName"
    }
}

Write-Output ("item models: {0} written, {1} unchanged, dir={2}" -f $written, $unchanged, $modelDir)
