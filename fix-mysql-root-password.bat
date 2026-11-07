@echo off
chcp 936 >nul
setlocal
rem ============================================================
rem  重置本机 MySQL 8.0 的 root 密码为 123456
rem  *** 必须右键“以管理员身份运行” ***
rem  原理：停服务 -> 用 --skip-grant-tables 启动 -> FLUSH PRIVILEGES ->
rem        ALTER USER root -> 停掉临时实例 -> 重新启动服务
rem ============================================================

set "MYSQLD=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe"
set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
set "NEWPWD=123456"

net session >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 请右键“以管理员身份运行”本脚本。
    pause
    exit /b 1
)

echo [1/6] 停止 MySQL80 服务 ...
net stop MySQL80

echo [2/6] 以跳过权限校验模式启动临时实例 ...
start "" /b "%MYSQLD%" --skip-grant-tables --skip-networking=0 --console
echo       等待 10 秒 ...
timeout /t 10 /nobreak >nul

echo [3/6] 重置 root 密码 ...
"%MYSQL%" --host=127.0.0.1 --port=3306 --user=root --skip-password --execute="FLUSH PRIVILEGES; ALTER USER 'root'@'localhost' IDENTIFIED BY '%NEWPWD%'; ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '%NEWPWD%';"
if errorlevel 1 (
    echo [ERROR] 重置失败，请把上面的报错发给管理员。
)

echo [4/6] 关闭临时实例 ...
taskkill /IM mysqld.exe /F >nul 2>&1
timeout /t 5 /nobreak >nul

echo [5/6] 重新启动 MySQL80 服务 ...
net start MySQL80
timeout /t 5 /nobreak >nul

echo [6/6] 用新密码验证 ...
"%MYSQL%" --host=127.0.0.1 --port=3306 --user=root --password=%NEWPWD% --execute="SELECT VERSION() AS mysql_version;"

echo.
echo 完成。root 密码现在是 %NEWPWD%
pause
