# Build Script for LambdaNotes Installer (Launch4j + Inno Setup)

$ErrorActionPreference = "Stop"

# --- Configuration ---
$launch4jPath = "C:\Program Files (x86)\Launch4j\launch4jc.exe"
$innoSetupPath = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
$outputDir = "installer_output"
$appDir = "$outputDir\LambdaNotes"

# --- Check Tools ---
if (-not (Test-Path $launch4jPath)) {
    Write-Warning "Launch4j not found at default location: $launch4jPath"
    if (Get-Command launch4jc.exe -ErrorAction SilentlyContinue) {
        $launch4jPath = "launch4jc.exe"
        Write-Host "Found Launch4j in PATH."
    } else {
        Write-Error "Launch4j is required but not found. Please install it or update the path in the script."
        exit 1
    }
}

if (-not (Test-Path $innoSetupPath)) {
    Write-Warning "Inno Setup not found at default location: $innoSetupPath"
    if (Get-Command ISCC.exe -ErrorAction SilentlyContinue) {
        $innoSetupPath = "ISCC.exe"
        Write-Host "Found Inno Setup in PATH."
    } else {
        Write-Error "Inno Setup is required but not found. Please install it or update the path in the script."
        exit 1
    }
}

# --- 1. Clean & Prepare ---
Write-Host "1. Cleaning previous builds..."
if (Test-Path $outputDir) {
    Remove-Item -Recurse -Force $outputDir
}
New-Item -ItemType Directory -Path $appDir | Out-Null
New-Item -ItemType Directory -Path "$appDir\app" | Out-Null
New-Item -ItemType Directory -Path "$appDir\backend" | Out-Null

# --- 2. Build Backend ---
Write-Host "2. Building Go Backend..."
Push-Location backend
go build -ldflags -H=windowsgui -o backend.exe main.go
if (-not (Test-Path "backend.exe")) {
    Write-Error "Go build failed!"
    exit 1
}
Copy-Item "backend.exe" -Destination "..\$appDir\backend\"
Pop-Location

# --- 3. Build Frontend ---
Write-Host "3. Building Java Frontend..."
Push-Location frontend
mvn clean package
if (-not (Test-Path "target/lambdanotes-1.0-SNAPSHOT.jar")) {
    Write-Error "Maven build failed!"
    exit 1
}
Copy-Item "target/lambdanotes-1.0-SNAPSHOT.jar" -Destination "..\$appDir\app\"
Pop-Location

# --- 4. Create Runtime (jlink) ---
Write-Host "4. Creating Custom Runtime (jlink)..."
# Modules required for a typical JavaFX app (even if shaded, we need the base platform)
$modules = "java.base,java.desktop,java.logging,java.scripting,java.xml,jdk.jsobject,jdk.unsupported,jdk.xml.dom,java.net.http,java.datatransfer,java.prefs"

# Find jlink
$jlink = "jlink"
if (-not (Get-Command jlink -ErrorAction SilentlyContinue)) {
    $possiblePaths = @(
        "$env:JAVA_HOME\bin\jlink.exe",
        "C:\Program Files\Java\jdk-25\bin\jlink.exe",
        "C:\Program Files\Java\jdk-21\bin\jlink.exe"
    )
    foreach ($p in $possiblePaths) {
        if (Test-Path $p) {
            $jlink = $p
            break
        }
    }
}

$runtimePath = "$appDir\runtime"
& $jlink --add-modules $modules --output $runtimePath --strip-debug --no-man-pages --no-header-files --compress=2

if (-not (Test-Path "$runtimePath\bin\java.exe")) {
    Write-Error "Failed to create runtime!"
    exit 1
}

# --- 5. Run Launch4j ---
Write-Host "5. Wrapping JAR with Launch4j..."
$launch4jConfig = "$PWD\installer_config\launch4j.xml"

# Try to run Launch4j using java -jar if possible (avoids JRE detection issues with launch4jc.exe)
$launch4jJar = Join-Path (Split-Path $launch4jPath -Parent) "launch4j.jar"
if (Test-Path $launch4jJar) {
    Write-Host "Using Launch4j JAR: $launch4jJar"
    java -jar $launch4jJar $launch4jConfig
} else {
    Write-Host "Using Launch4j Executable: $launch4jPath"
    & $launch4jPath $launch4jConfig
}

if (-not (Test-Path "$appDir\LambdaNotes.exe")) {
    Write-Error "Launch4j failed to create executable!"
    exit 1
}

# --- 5.5 Create Debug Script ---
Write-Host "5.5 Creating Debug Script..."
$debugContent = @"
@echo off
echo Starting LambdaNotes in Debug Mode...
echo.
"%~dp0runtime\bin\java.exe" -jar "%~dp0app\lambdanotes-1.0-SNAPSHOT.jar"
if %errorlevel% neq 0 (
    echo.
    echo Application exited with error code %errorlevel%
    pause
)
"@
Set-Content -Path "$appDir\debug.bat" -Value $debugContent

# --- 6. Run Inno Setup ---
Write-Host "6. Creating Installer with Inno Setup..."
$issFile = "$PWD\installer_config\setup.iss"
& $innoSetupPath $issFile

if (Test-Path "$outputDir\LambdaNotes_Setup.exe") {
    Write-Host "---------------------------------------------------"
    Write-Host "SUCCESS! Installer created at: $outputDir\LambdaNotes_Setup.exe"
    Write-Host "Portable version available at: $appDir"
    Write-Host "---------------------------------------------------"
} else {
    Write-Error "Inno Setup failed to create installer."
    exit 1
}
