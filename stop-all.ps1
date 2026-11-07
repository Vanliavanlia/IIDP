# ============================================================
#  IIDP 演示环境停止脚本
#  用法： powershell -NoProfile -ExecutionPolicy Bypass -File .\stop-all.ps1
#  先尝试优雅停止（MySQL / Redis / MinIO / Nginx），再按端口强制兜底
# ============================================================
$ErrorActionPreference = 'Continue'
$root = "D:\Code\intermediate_software"

function Test-Port([int]$port) {
    $c = New-Object System.Net.Sockets.TcpClient
    try { $c.Connect('127.0.0.1', $port); $c.Close(); return $true } catch { return $false }
}

function Stop-ByPort([int]$port, [string]$name) {
    $lines = netstat -ano | Select-String (":" + $port + "\s")
    $pids = @()
    foreach ($l in $lines) {
        $parts = ($l.Line -split '\s+') | Where-Object { $_ -ne '' }
        if ($parts.Length -ge 5 -and $parts[3] -eq 'LISTENING') { $pids += $parts[4] }
    }
    $pids = $pids | Sort-Object -Unique
    if ($pids.Count -eq 0) { Write-Host ("  " + $name + " 未运行"); return }
    foreach ($p in $pids) {
        try { Stop-Process -Id ([int]$p) -Force -ErrorAction Stop; Write-Host ("  " + $name + " pid=" + $p + " 已停止") }
        catch { Write-Host ("  " + $name + " pid=" + $p + " 停止失败: " + $_.Exception.Message) }
    }
}

Write-Host '=== 1/6 停止 MySQL 3307（优雅 shutdown） ==='
if (Test-Port 3307) {
    $mysqladmin = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqladmin.exe"
    if (Test-Path $mysqladmin) {
        & $mysqladmin --host=127.0.0.1 --port=3307 --user=root --password=123456 shutdown 2>&1 | Out-Null
        Start-Sleep -Seconds 3
    }
}
Stop-ByPort 3307 'MySQL'

Write-Host '=== 2/6 停止 Redis 6379（优雅 shutdown） ==='
if (Test-Port 6379) {
    $cli = Join-Path $root 'runtime\redis\redis-cli.exe'
    if (Test-Path $cli) { & $cli -p 6379 shutdown save 2>&1 | Out-Null; Start-Sleep -Seconds 2 }
}
Stop-ByPort 6379 'Redis'

Write-Host '=== 3/6 停止 MinIO 9000/9001 ==='
Stop-ByPort 9000 'MinIO-API'
Stop-ByPort 9001 'MinIO-Console'

Write-Host '=== 4/6 停止授权服务 8080 ==='
Stop-ByPort 8080 'grant'

Write-Host '=== 5/6 停止后端 8061 ==='
Stop-ByPort 8061 '后端'

Write-Host '=== 6/6 停止前端 Nginx 30666（优雅 -s stop） ==='
$nginx = Join-Path $root 'iidp-frontend-dist\nginx\nginx.exe'
if ((Test-Port 30666) -and (Test-Path $nginx)) {
    Push-Location (Join-Path $root 'iidp-frontend-dist')
    & $nginx -p "." -c "nginx\conf\nginx.conf" -s stop 2>&1 | Out-Null
    Pop-Location
    Start-Sleep -Seconds 2
}
Stop-ByPort 30666 'Nginx'

Write-Host ''
Write-Host '=== 检查残留端口 ==='
$left = @()
foreach ($p in 3307, 6379, 9000, 9001, 8080, 8061, 30666) { if (Test-Port $p) { $left += $p } }
if ($left.Count -eq 0) { Write-Host '全部已停止。' } else { Write-Host ("仍在监听: " + ($left -join ', ')) }