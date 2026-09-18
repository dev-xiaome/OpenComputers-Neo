# 获取 OpenComputers 的运行时依赖（OC-LuaJ / OC-JNLua 原生库）
#
# 这两个 jar 不随仓库分发（体积 + 上游 release 才是权威来源）。
# 用你本机可用的 GitHub 加速前缀；默认走 v4.gh-proxy.org。
#
# 用法：
#   .\tools\fetch-runtime-deps.ps1                 # 用默认代理
#   .\tools\fetch-runtime-deps.ps1 -Prefix ""      # 直连 GitHub
#   .\tools\fetch-runtime-deps.ps1 -Prefix "https://v4.gh-proxy.org/"

param(
    [string]$Project = (Split-Path -Parent $PSScriptRoot),
    [string]$Prefix  = 'https://v4.gh-proxy.org/'
)

$ErrorActionPreference = 'Stop'

$luajDir  = Join-Path $Project 'libs\luaj'
$jnluaDir = Join-Path $Project 'libs\jnlua'
New-Item -ItemType Directory -Force $luajDir, $jnluaDir | Out-Null

$targets = @(
    @{ Url = 'https://github.com/PC-Logix/OC-LuaJ/releases/latest/download/OC-LuaJ.jar';              Out = (Join-Path $luajDir  'OC-LuaJ.jar') },
    @{ Url = 'https://github.com/PC-Logix/OC-JNLua/releases/latest/download/OC-JNLua-Natives.jar';    Out = (Join-Path $jnluaDir 'OC-JNLua-Natives.jar') }
)

foreach ($t in $targets) {
    $url = $Prefix + $t.Url
    Write-Host "下载 $url"
    $aria = Get-Command aria2c -ErrorAction SilentlyContinue
    if ($aria) {
        & $aria.Source -x 16 -s 16 -d (Split-Path $t.Out -Parent) -o (Split-Path $t.Out -Leaf) $url `
            --console-log-level=warn --summary-interval=0 --allow-overwrite=true
    } else {
        Invoke-WebRequest -Uri $url -OutFile $t.Out -UseBasicParsing
    }
    if (Test-Path $t.Out) { Write-Host ("  OK  {0}  {1} KB" -f (Split-Path $t.Out -Leaf), [int]((Get-Item $t.Out).Length / 1KB)) }
    else { Write-Warning "  下载失败: $url" }
}

Write-Host ''
Write-Host '完成。接下来还需要准备 libs/deps 里的第三方集成依赖，见 README.md。'
