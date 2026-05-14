$ErrorActionPreference = 'SilentlyContinue'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$runDir = Join-Path $root '.run'
$metaFile = Join-Path $runDir 'dev-processes.json'

function Stop-ByPid {
  param([int]$PidToStop)

  if ($PidToStop -le 0) { return }

  $process = Get-Process -Id $PidToStop -ErrorAction SilentlyContinue
  if ($process) {
    Stop-Process -Id $PidToStop -Force -ErrorAction SilentlyContinue
    Write-Host "Stopped PID: $PidToStop"
  }
}

function Stop-ByPort {
  param([int]$Port)

  $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  foreach ($listener in $listeners) {
    if ($listener.OwningProcess -gt 0) {
      Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
      Write-Host "Stopped process on port $Port (PID: $($listener.OwningProcess))"
    }
  }
}

if (Test-Path $metaFile) {
  $meta = Get-Content -Path $metaFile -Raw | ConvertFrom-Json
  Stop-ByPid -PidToStop ([int]$meta.backendPid)
  Stop-ByPid -PidToStop ([int]$meta.frontendPid)
  Stop-ByPid -PidToStop ([int]$meta.yoloPid)
  Stop-ByPid -PidToStop ([int]$meta.mmdet3dPid)
} else {
  Write-Host 'No meta file found. Trying port-based stop only.'
}

Stop-ByPort -Port 8080
Stop-ByPort -Port 3000
Stop-ByPort -Port 9000
Stop-ByPort -Port 8000

if (Test-Path $metaFile) {
  Remove-Item -Path $metaFile -Force
}

Write-Host 'Done. Backend, frontend, YOLO, and MMDet3D stop sequence completed.'
