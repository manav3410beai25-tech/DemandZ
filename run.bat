@echo off
setlocal enabledelayedexpansion
title DemandZ

cd /d "%~dp0"

echo ========================================================
echo                 DemandZ Launcher
echo ========================================================

:: Check for Java
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java was not found in your PATH.
    echo Please ensure Java 21 or later is installed.
    pause
    exit /b 1
)

:: Ensure target\classes directory exists
if not exist "target\classes" mkdir "target\classes"

:: Compile Java files directly
echo Compiling source files...
javac -encoding UTF-8 -cp "lib\*;src" -d target\classes src\Main.java src\config\*.java src\dao\*.java src\database\*.java src\model\*.java src\service\*.java src\storage\*.java src\ui\*.java

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Compilation failed. Please check the error messages above.
    pause
    exit /b %ERRORLEVEL%
)

:: Launch DemandZ (passes any arguments like --cli, --gui, --migrate-csv)
echo Starting DemandZ...
java -cp "target\classes;lib\*" Main %*

if %ERRORLEVEL% neq 0 (
    echo.
    echo [INFO] DemandZ exited with code %ERRORLEVEL%.
    pause
)
