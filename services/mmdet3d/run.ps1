param(
    [string]$ProjectRoot = $PSScriptRoot,
    [string]$Python = $env:PYTHON
)

$ErrorActionPreference = "Stop"

Set-Location $ProjectRoot

if (-not $env:MMDET3D_ROOT) {
    $LocalMmdet3d = Join-Path $ProjectRoot "third_party\mmdetection3d"
    $SiblingMmdet3d = Join-Path (Split-Path -Parent $ProjectRoot) "MMDetection3D\mmdetection3d"
    if (Test-Path $LocalMmdet3d) {
        $env:MMDET3D_ROOT = $LocalMmdet3d
    } elseif (Test-Path $SiblingMmdet3d) {
        $env:MMDET3D_ROOT = $SiblingMmdet3d
    }
}

if (-not $env:KITTI_DATASET_ROOT) {
    $LocalKitti = Join-Path $ProjectRoot "data\kitti"
    $LegacyKitti = "F:\YOLO\kitty"
    if (Test-Path $LocalKitti) {
        $env:KITTI_DATASET_ROOT = $LocalKitti
    } elseif (Test-Path $LegacyKitti) {
        $env:KITTI_DATASET_ROOT = $LegacyKitti
    }
}

if (-not $env:DEVICE) {
    $env:DEVICE = "cuda:0"
}

function Test-PythonTorch([string]$Exe, [string[]]$Args = @()) {
    try {
        & $Exe @Args -c "import torch; print(torch.__version__)" 1>$null 2>$null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

if (-not $Python) {
    $Candidates = @(
        @{ Exe = "py"; Args = @("-3.7") },
        @{ Exe = "C:\Users\Shienroxic\AppData\Local\Programs\Python\Python37\python.exe"; Args = @() },
        @{ Exe = "python"; Args = @() }
    )

    foreach ($Candidate in $Candidates) {
        if ($Candidate.Exe -eq "py") {
            $PyLauncher = Get-Command py -ErrorAction SilentlyContinue
            if ($PyLauncher -and (Test-PythonTorch -Exe $Candidate.Exe -Args $Candidate.Args)) {
                $Python = $Candidate.Exe
                $PythonArgs = $Candidate.Args
                break
            }
        } else {
            $PathExists = Test-Path $Candidate.Exe
            if ($PathExists -and (Test-PythonTorch -Exe $Candidate.Exe -Args $Candidate.Args)) {
                $Python = $Candidate.Exe
                $PythonArgs = $Candidate.Args
                break
            }
        }
    }
}

if (-not $Python) {
    throw "No Python interpreter with torch was found. Set PYTHON to the correct interpreter, or install torch for Python 3.7."
}

if (-not $PythonArgs) {
    $PythonArgs = @()
}

Write-Host "Using Python: $Python $($PythonArgs -join ' ')" -ForegroundColor Cyan
Write-Host "Using MMDET3D_ROOT: $env:MMDET3D_ROOT" -ForegroundColor Cyan
Write-Host "Using KITTI_DATASET_ROOT: $env:KITTI_DATASET_ROOT" -ForegroundColor Cyan
& $Python @PythonArgs -m uvicorn app.main:app --host 0.0.0.0 --port 8000
