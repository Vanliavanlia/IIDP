# ============================================================
#  IIDP 演示环境一键启动脚本
#  启动顺序：MySQL(3307) -> Redis(6379) -> MinIO(9000)
#            -> 授权服务 grant(8080) -> 后端(8061) -> Nginx(30666)
#  用法（在 PowerShell 里执行）：
#      powershell -NoProfile -ExecutionPolicy Bypass -File .\start-all.ps1
# ============================================================
$ErrorActionPreference = 'Continue'

$root      = 'D:\Code\intermediate_software'
$mysqlBin  = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$javaExe   = 'D:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot\bin\java.exe'
$nginxDir  = Join-Path $root 'iidp-frontend-dist'
$backendDir= Join-Path $root 'iidp-backend-demo'
$backendJar= Join-Path $backendDir 'sie-iidp-demo-start\target\sie-iidp-demo-start-1.0-SNAPSHOT.jar'

function Test-Port([int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect('127.0.0.1', $port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne(600)) { return $false }
        $client.EndConnect($iar)
        return $true
    } catch { return $false } finally { $client.Close() }
}

function Wait-Port([int]$port, [int]$timeoutSec) {
    for ($i = 0; $i -lt $timeoutSec; $i++) {
        if (Test-Port $port) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

Write-Host '=== 1/6 MySQL 3307 ==='
if (Test-Port 3307) {
    Write-Host '  MySQL 已在运行'
} else {
    Start-Process -FilePath (Join-Path $mysqlBin 'mysqld.exe') `
        -ArgumentList "--defaults-file=`"$root\mysql-iidp.ini`"" `
        -WorkingDirectory $root -WindowStyle Hidden
    if (Wait-Port 3307 60) { Write-Host '  MySQL 启动成功' } else { Write-Host '  MySQL 启动超时，见 mysql-data\error.log' }
}

Write-Host '=== 2/6 Redis 6379 ==='
if (Test-Port 6379) {
    Write-Host '  Redis 已在运行'
} else {
    Start-Process -FilePath (Join-Path $root 'runtime\redis\redis-server.exe') `
        -ArgumentList "`"$root\runtime\redis\redis-iidp.conf`"" -WindowStyle Hidden
    if (Wait-Port 6379 20) { Write-Host '  Redis 启动成功' } else { Write-Host '  Redis 启动失败' }
}

Write-Host '=== 3/6 MinIO 对象存储 9000 ==='
if (Test-Port 9000) {
    Write-Host '  MinIO 已在运行'
} else {
    # 凭据必须与 application-dev.properties 里的 minio.accessKey / minio.secretKey 一致
    $env:MINIO_ROOT_USER     = 'snest'
    $env:MINIO_ROOT_PASSWORD = '12345678'
    Start-Process -FilePath (Join-Path $root 'runtime\minio\minio.exe') `
        -ArgumentList 'server', (Join-Path $root 'runtime\minio\data'), '--address', '127.0.0.1:9000', '--console-address', '127.0.0.1:9001' `
        -WorkingDirectory (Join-Path $root 'runtime\minio') -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $root 'runtime\minio-console.log') `
        -RedirectStandardError  (Join-Path $root 'runtime\minio-console-err.log')
    if (Wait-Port 9000 60) { Write-Host '  MinIO 启动成功（控制台 http://127.0.0.1:9001，snest / 12345678）' } else { Write-Host '  MinIO 启动超时，见 runtime\minio-console-err.log' }
}

Write-Host '=== 4/6 授权服务 grant 8080 ==='
if (Test-Port 8080) {
    Write-Host '  grant 已在运行'
} else {
    Start-Process -FilePath (Join-Path $nginxDir 'grant\grant-dev.exe') `
        -WorkingDirectory $nginxDir -WindowStyle Hidden
    if (Wait-Port 8080 60) { Write-Host '  grant 启动成功' } else { Write-Host '  grant 启动超时' }
}

Write-Host '=== 5/6 后端 8061 ==='
if (Test-Port 8061) {
    Write-Host '  后端已在运行'
} else {
    Start-Process -FilePath $javaExe `
        -ArgumentList '-Dfile.encoding=UTF-8','-Duser.timezone=Asia/Shanghai','-jar',$backendJar `
        -WorkingDirectory $backendDir -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $root 'runtime\backend-console.log') `
        -RedirectStandardError  (Join-Path $root 'runtime\backend-console-err.log')
    if (Wait-Port 8061 180) { Write-Host '  后端启动成功' } else { Write-Host '  后端启动超时，见 runtime\backend-console.log' }
}

Write-Host '=== 6/6 Nginx 30666 ==='
if (Test-Port 30666) {
    Write-Host '  Nginx 已在运行'
} else {
    $ng = Join-Path $nginxDir 'nginx\nginx.exe'
    Start-Process -FilePath $ng -ArgumentList '-p','.','-c','nginx\conf\nginx.conf' -WorkingDirectory $nginxDir -WindowStyle Hidden
    if (Wait-Port 30666 20) { Write-Host '  Nginx 启动成功' } else { Write-Host '  Nginx 启动失败' }
}

Write-Host ''
Write-Host '启动完成，访问： http://localhost:30666/iidp/'
Write-Host '账号： implementer / Implementer_2023    admin / admin    superuser / superuser'
