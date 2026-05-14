param(
  [switch]$Watch,
  [int]$IntervalSec = 5
)

$ErrorActionPreference = 'SilentlyContinue'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$runDir = Join-Path $root '.run'
$metaFile = Join-Path $runDir 'dev-processes.json'

function Get-PortInfo {
  param([int]$Port)

  $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($listener) {
    return [PSCustomObject]@{
      Port = $Port
      Listening = $true
      OwningProcess = $listener.OwningProcess
    }
  }

  return [PSCustomObject]@{
    Port = $Port
    Listening = $false
    OwningProcess = $null
  }
}

function Get-HttpStatus {
  param([string]$Url)

  try {
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
    return "$($response.StatusCode) $($response.StatusDescription)"
  } catch {
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
      $code = [int]$_.Exception.Response.StatusCode
      return "$code (reachable)"
    }
    return 'unreachable'
  }
}

function Get-ProcessNameSafe {
  param([int]$ProcessId)

  if ($ProcessId -le 0) { return '' }
  $p = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
  if ($p) { return $p.ProcessName }
  return 'not found'
}

function Read-Meta {
  if (Test-Path $metaFile) {
    try {
      return Get-Content -Path $metaFile -Raw | ConvertFrom-Json
    } catch {
      return $null
    }
  }
  return $null
}

function Show-Status {
  $services = @(
    @{ Name = 'backend'; Port = 8080; Url = 'http://127.0.0.1:8080'; MetaKey = 'backendPid' },
    @{ Name = 'frontend'; Port = 3000; Url = 'http://127.0.0.1:3000'; MetaKey = 'frontendPid' },
    @{ Name = 'yolo'; Port = 9000; Url = 'http://127.0.0.1:9000/health'; MetaKey = 'yoloPid' },
    @{ Name = 'mmdet3d'; Port = 8000; Url = 'http://127.0.0.1:8000/health'; MetaKey = 'mmdet3dPid' }
  )

  $meta = Read-Meta
  $rows = foreach ($service in $services) {
    $portInfo = Get-PortInfo -Port $service.Port
    $scriptPid = $null
    if ($meta) {
      $scriptPid = [int]($meta.($service.MetaKey))
    }

    [PSCustomObject]@{
      Service = $service.Name
      Port = $service.Port
      Listening = $portInfo.Listening
      Http = (Get-HttpStatus -Url $service.Url)
      PortPid = $portInfo.OwningProcess
      PortProcess = (Get-ProcessNameSafe -ProcessId $portInfo.OwningProcess)
      ScriptPid = $scriptPid
      ScriptPidProcess = (Get-ProcessNameSafe -ProcessId $scriptPid)
    }
  }

  Write-Host "Time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
  if ($meta -and $meta.startedAt) {
    Write-Host "StartedAt(from meta): $($meta.startedAt)"
  } else {
    Write-Host 'StartedAt(from meta): n/a'
  }

  ($rows | Format-Table -AutoSize | Out-String) | Write-Host
}

if ($IntervalSec -lt 1) { $IntervalSec = 1 }

if ($Watch) {
  while ($true) {
    Clear-Host
    Show-Status
    Start-Sleep -Seconds $IntervalSec
  }
} else {
  Show-Status
}
