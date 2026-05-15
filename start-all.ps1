param(
  [int]$TimeoutSec = 120,
  [int]$PollIntervalMs = 1000
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $root 'backend'
$frontendDir = Join-Path $root 'frontend'
$yoloDir = Join-Path $root 'services\yolo-http'
$mmdet3dDir = Join-Path $root 'services\mmdet3d'
$runDir = Join-Path $root '.run'
$metaFile = Join-Path $runDir 'dev-processes.json'
$envFile = Join-Path $root '.env.local'

$jdkCandidates = @(
  'E:\BD\IntelliJ IDEA 2023.3.4\jbr',
  'C:\Users\Shienroxic\.jdks\openjdk-21.0.2',
  'C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
)
$jdkHome = $jdkCandidates | Where-Object { Test-Path (Join-Path $_ 'bin\java.exe') } | Select-Object -First 1
$nodeBin = 'C:\Program Files\nodejs'
$mavenHome = Join-Path (Split-Path -Parent $root) 'apache-maven-3.9.12'
$mavenSettings = Join-Path $backendDir 'build-support\maven-global-settings.xml'

if (-not $jdkHome) { throw "Java not found. Checked: $($jdkCandidates -join ', ')" }

$javaExe = Join-Path $jdkHome 'bin\java.exe'
$nodeExe = Join-Path $nodeBin 'node.exe'
$npmCmd = Join-Path $nodeBin 'npm.cmd'
$mvnCmd = Join-Path $mavenHome 'bin\mvn.cmd'

if (!(Test-Path $mvnCmd)) {
  $mvnCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
  if ($mvnCommand) {
    $mvnCmd = $mvnCommand.Source
  }
}

foreach ($requiredPath in @($backendDir, $frontendDir, $yoloDir, $mmdet3dDir)) {
  if (!(Test-Path $requiredPath)) {
    throw "Required directory not found: $requiredPath"
  }
}
if (!(Test-Path $javaExe)) { throw "Java not found: $javaExe" }
if (!(Test-Path $nodeExe)) { throw "Node.js not found: $nodeExe" }
if (!(Test-Path $npmCmd)) { throw "npm not found: $npmCmd" }
if (!(Test-Path $mvnCmd)) { throw "Maven not found: $mvnCmd" }
if (!(Test-Path $mavenSettings)) { throw "Maven settings not found: $mavenSettings" }

if ($TimeoutSec -lt 5) { $TimeoutSec = 5 }
if ($PollIntervalMs -lt 200) { $PollIntervalMs = 200 }

function Import-EnvFile {
  param([string]$Path)

  if (!(Test-Path $Path)) {
    return
  }

  Get-Content $Path | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith('#')) {
      return
    }

    $separatorIndex = $line.IndexOf('=')
    if ($separatorIndex -lt 1) {
      return
    }

    $name = $line.Substring(0, $separatorIndex).Trim()
    $value = $line.Substring($separatorIndex + 1).Trim()

    if (
      ($value.StartsWith('"') -and $value.EndsWith('"')) -or
      ($value.StartsWith("'") -and $value.EndsWith("'"))
    ) {
      $value = $value.Substring(1, $value.Length - 2)
    }

    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
  }

  Write-Host "Loaded local environment variables from: $Path"
}

Import-EnvFile -Path $envFile

New-Item -Path $runDir -ItemType Directory -Force | Out-Null

$backendLogOut = Join-Path $runDir 'backend.out.log'
$backendLogErr = Join-Path $runDir 'backend.err.log'
$frontendLogOut = Join-Path $runDir 'frontend.out.log'
$frontendLogErr = Join-Path $runDir 'frontend.err.log'
$yoloLogOut = Join-Path $runDir 'yolo.out.log'
$yoloLogErr = Join-Path $runDir 'yolo.err.log'
$mmdet3dLogOut = Join-Path $runDir 'mmdet3d.out.log'
$mmdet3dLogErr = Join-Path $runDir 'mmdet3d.err.log'

function Resolve-YoloPython {
  $candidates = @(
    @{ Exe = 'py'; Args = @('-3.11') },
    @{ Exe = 'py'; Args = @('-3.10') },
    @{ Exe = 'python'; Args = @() }
  )

  foreach ($candidate in $candidates) {
    try {
      $candidateArgs = $candidate.Args
      & $candidate.Exe @candidateArgs -c "import sys; print(sys.version)" 1>$null 2>$null
      if ($LASTEXITCODE -eq 0) {
        return $candidate
      }
    } catch {
    }
  }

  throw 'No usable Python interpreter found for YOLO service.'
}

function Resolve-MmdetPython {
  $candidates = @(
    @{ Exe = 'py'; Args = @('-3.7') },
    @{ Exe = 'C:\Users\Shienroxic\AppData\Local\Programs\Python\Python37\python.exe'; Args = @() },
    @{ Exe = 'python'; Args = @() }
  )

  foreach ($candidate in $candidates) {
    try {
      $candidateArgs = $candidate.Args
      & $candidate.Exe @candidateArgs -c "import torch; print(torch.__version__)" 1>$null 2>$null
      if ($LASTEXITCODE -eq 0) {
        return $candidate
      }
    } catch {
    }
  }

  throw 'No usable Python interpreter with torch found for MMDet3D service.'
}

function Test-PortListening {
  param([int]$Port)

  $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  return [bool]$listener
}

function Test-HttpReachable {
  param(
    [string]$Url,
    [int[]]$AllowedStatusCodes
  )

  try {
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
    return ($AllowedStatusCodes -contains [int]$response.StatusCode)
  } catch {
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
      $code = [int]$_.Exception.Response.StatusCode
      return ($AllowedStatusCodes -contains $code)
    }
    return $false
  }
}

function Stop-IfRunning {
  param([int]$ProcessId)

  if ($ProcessId -le 0) { return }
  $p = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
  if ($p) {
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
  }
}

function Stop-ByPort {
  param([int]$Port)

  $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  foreach ($listener in $listeners) {
    if ($listener.OwningProcess -gt 0) {
      Stop-Process -Id $listener.OwningProcess -Force -ErrorAction SilentlyContinue
    }
  }
}

function Wait-ServiceReady {
  param(
    [string]$Name,
    [int]$Port,
    [string]$Url,
    [int[]]$AllowedStatusCodes,
    [int]$ParentProcessId,
    [int]$TimeoutSeconds,
    [int]$IntervalMs
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $parentAlive = Get-Process -Id $ParentProcessId -ErrorAction SilentlyContinue
    if (-not $parentAlive) {
      throw "$Name start process exited early."
    }

    $portOk = Test-PortListening -Port $Port
    $httpOk = Test-HttpReachable -Url $Url -AllowedStatusCodes $AllowedStatusCodes

    if ($portOk -and $httpOk) {
      Write-Host "$Name ready: $Url"
      return
    }

    Start-Sleep -Milliseconds $IntervalMs
  }

  throw "$Name startup timeout after $TimeoutSeconds seconds."
}

$yoloPython = Resolve-YoloPython
$mmdetPython = Resolve-MmdetPython
$localYoloModels = @(
  (Join-Path $yoloDir 'best.pt'),
  (Join-Path $yoloDir 'yolo26m.pt')
)
$localMmdetRoot = Join-Path $mmdet3dDir 'third_party\mmdetection3d'
$legacyMmdetRoot = 'F:\YOLO\kitty\MMDetection3D\mmdetection3d'
$localKittiRoot = Join-Path $mmdet3dDir 'data\kitti'
$legacyKittiRoot = 'F:\YOLO\kitty'

$resolvedYoloModel = $null
foreach ($candidate in $localYoloModels) {
  if (Test-Path $candidate) {
    $resolvedYoloModel = $candidate
    break
  }
}

if (-not $resolvedYoloModel) {
  throw 'YOLO model not found. Expected services\yolo-http\best.pt or services\yolo-http\yolo26m.pt.'
}

$resolvedMmdetRoot = $legacyMmdetRoot
if (Test-Path $localMmdetRoot) {
  $resolvedMmdetRoot = $localMmdetRoot
}

$resolvedKittiRoot = $null
if (Test-Path $localKittiRoot) {
  $resolvedKittiRoot = $localKittiRoot
} elseif (Test-Path $legacyKittiRoot) {
  $resolvedKittiRoot = $legacyKittiRoot
}

$backendCommand = @"
`$env:JAVA_HOME = '$jdkHome'
`$javaBin = Join-Path `$env:JAVA_HOME 'bin'
`$env:Path = `$javaBin + ';' + `$env:Path
`$env:SPRING_PROFILES_ACTIVE = 'local'
Set-Location '$backendDir'
& '$mvnCmd' -gs '$mavenSettings' -DskipTests spring-boot:run
"@

$frontendCommand = @"
`$env:Path = '$nodeBin;`$env:Path'
Set-Location '$frontendDir'
& '$npmCmd' run dev -- --host
"@

$yoloArgsLiteral = if ($yoloPython.Args.Count -gt 0) {
  "@('" + ($yoloPython.Args -join "','") + "')"
} else {
  "@()"
}
$yoloCommand = @"
`$env:PORT = '9000'
`$env:MODEL_PATH = '$resolvedYoloModel'
`$pythonArgs = $yoloArgsLiteral
Set-Location '$yoloDir'
& '$($yoloPython.Exe)' @pythonArgs app.py
"@

$mmdetArgsLiteral = if ($mmdetPython.Args.Count -gt 0) {
  "@('" + ($mmdetPython.Args -join "','") + "')"
} else {
  "@()"
}
$mmdet3dCommand = @"
`$env:MMDET3D_ROOT = '$resolvedMmdetRoot'
`$env:DEVICE = 'cuda:0'
if ('$resolvedKittiRoot' -ne '') { `$env:KITTI_DATASET_ROOT = '$resolvedKittiRoot' }
`$pythonArgs = $mmdetArgsLiteral
Set-Location '$mmdet3dDir'
& '$($mmdetPython.Exe)' @pythonArgs -m uvicorn app.main:app --host 0.0.0.0 --port 8000
"@

$backendProcess = Start-Process -FilePath 'powershell.exe' `
  -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $backendCommand `
  -PassThru -WindowStyle Minimized `
  -RedirectStandardOutput $backendLogOut -RedirectStandardError $backendLogErr

$frontendProcess = Start-Process -FilePath 'powershell.exe' `
  -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $frontendCommand `
  -PassThru -WindowStyle Minimized `
  -RedirectStandardOutput $frontendLogOut -RedirectStandardError $frontendLogErr

$yoloProcess = Start-Process -FilePath 'powershell.exe' `
  -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $yoloCommand `
  -PassThru -WindowStyle Minimized `
  -RedirectStandardOutput $yoloLogOut -RedirectStandardError $yoloLogErr

$mmdet3dProcess = Start-Process -FilePath 'powershell.exe' `
  -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $mmdet3dCommand `
  -PassThru -WindowStyle Minimized `
  -RedirectStandardOutput $mmdet3dLogOut -RedirectStandardError $mmdet3dLogErr

$meta = [PSCustomObject]@{
  backendPid = $backendProcess.Id
  frontendPid = $frontendProcess.Id
  yoloPid = $yoloProcess.Id
  mmdet3dPid = $mmdet3dProcess.Id
  startedAt = (Get-Date).ToString('s')
  backendOutLog = $backendLogOut
  backendErrLog = $backendLogErr
  frontendOutLog = $frontendLogOut
  frontendErrLog = $frontendLogErr
  yoloOutLog = $yoloLogOut
  yoloErrLog = $yoloLogErr
  mmdet3dOutLog = $mmdet3dLogOut
  mmdet3dErrLog = $mmdet3dLogErr
}

$meta | ConvertTo-Json | Set-Content -Path $metaFile -Encoding UTF8

try {
  Wait-ServiceReady -Name 'backend' -Port 8080 -Url 'http://127.0.0.1:8080' -AllowedStatusCodes @(200, 401, 403, 404) `
    -ParentProcessId $backendProcess.Id -TimeoutSeconds $TimeoutSec -IntervalMs $PollIntervalMs
  Wait-ServiceReady -Name 'frontend' -Port 3000 -Url 'http://127.0.0.1:3000' -AllowedStatusCodes @(200) `
    -ParentProcessId $frontendProcess.Id -TimeoutSeconds $TimeoutSec -IntervalMs $PollIntervalMs
  Wait-ServiceReady -Name 'yolo' -Port 9000 -Url 'http://127.0.0.1:9000/health' -AllowedStatusCodes @(200) `
    -ParentProcessId $yoloProcess.Id -TimeoutSeconds $TimeoutSec -IntervalMs $PollIntervalMs
  Wait-ServiceReady -Name 'mmdet3d' -Port 8000 -Url 'http://127.0.0.1:8000/health' -AllowedStatusCodes @(200) `
    -ParentProcessId $mmdet3dProcess.Id -TimeoutSeconds $TimeoutSec -IntervalMs $PollIntervalMs
} catch {
  Write-Error $_.Exception.Message
  Stop-IfRunning -ProcessId $backendProcess.Id
  Stop-IfRunning -ProcessId $frontendProcess.Id
  Stop-IfRunning -ProcessId $yoloProcess.Id
  Stop-IfRunning -ProcessId $mmdet3dProcess.Id
  Stop-ByPort -Port 8080
  Stop-ByPort -Port 3000
  Stop-ByPort -Port 9000
  Stop-ByPort -Port 8000
  if (Test-Path $metaFile) { Remove-Item -Path $metaFile -Force }
  exit 1
}

Write-Host "Started backend PID: $($backendProcess.Id)"
Write-Host "Started frontend PID: $($frontendProcess.Id)"
Write-Host "Started yolo PID: $($yoloProcess.Id)"
Write-Host "Started mmdet3d PID: $($mmdet3dProcess.Id)"
Write-Host "Meta file: $metaFile"
Write-Host "Backend URL: http://127.0.0.1:8080"
Write-Host "Frontend URL: http://127.0.0.1:3000"
Write-Host "YOLO URL: http://127.0.0.1:9000"
Write-Host "MMDet3D URL: http://127.0.0.1:8000"
Write-Host "Startup check: passed"
