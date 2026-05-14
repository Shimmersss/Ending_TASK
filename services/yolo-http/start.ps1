$ErrorActionPreference = "Stop"

if (-not $env:MODEL_PATH) {
    $localModel = Join-Path $PSScriptRoot "best.pt"
    if (Test-Path $localModel) {
        $env:MODEL_PATH = $localModel
    } else {
        $fallbackModel = Join-Path $PSScriptRoot "yolo26m.pt"
        if (Test-Path $fallbackModel) {
            $env:MODEL_PATH = $fallbackModel
        }
    }
}

if (-not $env:PORT) {
    $env:PORT = "9000"
}

python app.py
