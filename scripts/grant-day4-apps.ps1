<#
  给平台角色补授业务 APP 的访问权限（第四天 5 APP 架构）
  背景：iam 种子只给 rbac_role_implementer_admin / rbac_role_admin 授了平台应用，
        业务 APP（code / cms / mes / qms / sie-snest-wms）需要单独补 rbac_role_auth，
        否则这两个角色的账号登录后看不到业务菜单。
  用法：先启动整套服务（.\start-all.ps1），然后
        powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\grant-day4-apps.ps1
  幂等：已存在的 (role, app_name) 组合会跳过。
#>

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$base   = 'http://127.0.0.1:30666/api'
$login  = 'implementer'
$pwdMd5 = '059b7e90b851797c848e610a212cb915'   # MD5('Implementer_2023')
$roles  = @('rbac_role_implementer_admin', 'rbac_role_admin')
$apps   = @('code', 'cms', 'mes', 'qms', 'sie-snest-wms')
$tenant = 'rbac_tenant_root'

function Invoke-RpcBody {
    param([string]$Url, [string]$Body, [string]$Token)

    $tmp = Join-Path $env:TEMP ('rpc_' + [guid]::NewGuid().ToString('N') + '.json')
    [System.IO.File]::WriteAllText($tmp, $Body, (New-Object System.Text.UTF8Encoding($false)))
    try {
        $curlArgs = @('-s', '-X', 'POST', $Url, '-H', 'Content-Type: application/json', '--data-binary', "@$tmp")
        if ($Token) {
            $curlArgs += @('-H', "Authorization: Bearer $Token", '-H', "token: $Token")
        }
        $out = & curl.exe @curlArgs
        return ($out | Out-String)
    }
    finally {
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    }
}

# 1. 登录拿 token
$loginBody = '{"jsonrpc":"2.0","method":"login","id":"1","params":{"login":"' + $login + '","password":"' + $pwdMd5 + '","app":"sie-iidp-iam","tag":"master","context":{"lang":"zh_CN","tenant":"root"}}}'
$loginResp = (Invoke-RpcBody -Url "$base/root/rpc/login?model=&service=" -Body $loginBody) | ConvertFrom-Json
if ($loginResp.error) { throw ("登录失败：" + ($loginResp.error | ConvertTo-Json -Compress)) }
$token = $loginResp.result.data.token
Write-Host "[1/3] 登录成功：$login （token $($token.Length) 位）"

# 2. 查已有的角色授权
$searchBody = '{"jsonrpc":"2.0","method":"service","id":"2","params":{"args":{"filter":[],"properties":["role","appName"],"limit":10000,"offset":0},"app":"sie-iidp-iam","service":"search","model":"rbac_role_auth","tag":"master","context":{"lang":"zh_CN","tenant":"root"}}}'
$searchResp = (Invoke-RpcBody -Url "$base/root/rpc/service/?model=rbac_role_auth&service=search" -Body $searchBody -Token $token) | ConvertFrom-Json
if ($searchResp.error) { throw ("查询角色授权失败：" + ($searchResp.error | ConvertTo-Json -Compress)) }

$existing = @{}
foreach ($row in $searchResp.result.data) {
    $existing[($row.role + '|' + $row.appName)] = $true
}

# 3. 只补缺失的
$rows = @()
foreach ($r in $roles) {
    foreach ($a in $apps) {
        if ($existing.ContainsKey($r + '|' + $a)) {
            Write-Host ("      跳过（已存在）：$r -> $a")
            continue
        }
        $rows += ('{"role":"' + $r + '","appName":"' + $a + '","appTag":"master","appAll":"1","tenantId":"' + $tenant + '"}')
    }
}

if ($rows.Count -eq 0) {
    Write-Host '[2/3] 无需补充，角色授权已完整'
    return
}

$createBody = '{"jsonrpc":"2.0","method":"service","id":"3","params":{"args":{"valuesList":[' + ($rows -join ',') + ']},"app":"sie-iidp-iam","service":"create","model":"rbac_role_auth","tag":"master","context":{"lang":"zh_CN","tenant":"root"}}}'
$createResp = (Invoke-RpcBody -Url "$base/root/rpc/service/?model=rbac_role_auth&service=create" -Body $createBody -Token $token) | ConvertFrom-Json
if ($createResp.error) { throw ("补授权失败：" + ($createResp.error | ConvertTo-Json -Compress)) }

Write-Host ("[2/3] 已补充 " + $rows.Count + " 条角色授权")
Write-Host ("      新增 id：" + (@($createResp.result.data) -join ', '))
Write-Host '[3/3] 完成，重新登录前端即可看到 5 个业务 APP 的菜单'
