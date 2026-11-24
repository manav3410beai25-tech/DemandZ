# DemandZ PowerShell Launcher
Set-Location -Path $PSScriptRoot

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "                 DemandZ Launcher" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# Check Java
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Java was not found in your PATH. Please install Java 21 or later." -ForegroundColor Red
    pause
    exit 1
}

# Create output folder
if (-not (Test-Path "target\classes")) {
    New-Item -ItemType Directory -Path "target\classes" -Force | Out-Null
}

# Compile
Write-Host "Compiling source files..." -ForegroundColor Yellow
javac -encoding UTF-8 -cp "lib\*;src" -d "target\classes" src\Main.java src\config\*.java src\dao\*.java src\database\*.java src\model\*.java src\service\*.java src\storage\*.java src\ui\*.java
$compileStatus = $LASTEXITCODE

if ($compileStatus -ne 0) {
    Write-Host "`n[ERROR] Compilation failed. Please check the error messages above." -ForegroundColor Red
    pause
    exit $compileStatus
}

# Run
Write-Host "Starting DemandZ..." -ForegroundColor Green
java -cp "target\classes;lib\*" Main $args
