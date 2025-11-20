# Build Script for LambdaNotes Installer

$ErrorActionPreference = "Stop"

Write-Host "1. Building Go Backend..."
Set-Location backend
go build -o backend.exe main.go
if (-not (Test-Path "backend.exe")) {
    Write-Error "Go build failed!"
    exit 1
}
Set-Location ..

Write-Host "2. Building Java Frontend..."
Set-Location frontend
mvn clean package
if (-not (Test-Path "target/lambdanotes-1.0-SNAPSHOT.jar")) {
    Write-Error "Maven build failed!"
    exit 1
}
Set-Location ..

Write-Host "3. Preparing Input Directory..."
$inputDir = "installer_input"
if (Test-Path $inputDir) {
    Remove-Item -Recurse -Force $inputDir
}
New-Item -ItemType Directory -Path $inputDir | Out-Null

# Copy files
Copy-Item "backend/backend.exe" -Destination $inputDir
Copy-Item "frontend/target/lambdanotes-1.0-SNAPSHOT.jar" -Destination $inputDir

Write-Host "4. Running jpackage..."
# Ensure jpackage is available
$jpackage = "jpackage"
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    # Try to find it in known locations
    $possiblePaths = @(
        "C:\Program Files\Java\jdk-25\bin\jpackage.exe",
        "$env:JAVA_HOME\bin\jpackage.exe"
    )
    
    foreach ($path in $possiblePaths) {
        if (Test-Path $path) {
            $jpackage = $path
            break
        }
    }
    
    if (-not (Get-Command $jpackage -ErrorAction SilentlyContinue) -and -not (Test-Path $jpackage)) {
        Write-Error "jpackage not found! Please ensure JDK 14+ is installed and in PATH."
        exit 1
    }
}

Write-Host "Using jpackage: $jpackage"

# Create output directory
$outputDir = "installer_output"
if (Test-Path $outputDir) {
    Remove-Item -Recurse -Force $outputDir
}
New-Item -ItemType Directory -Path $outputDir | Out-Null

# Check for WiX Toolset
$type = "app-image"
$wixPath = "C:\Program Files (x86)\WiX Toolset v3.11\bin"
if (Get-Command light -ErrorAction SilentlyContinue) {
    Write-Host "WiX Toolset detected in PATH. Building MSI installer..."
    $type = "msi"
} elseif (Test-Path $wixPath) {
    Write-Host "WiX Toolset detected at $wixPath. Adding to PATH..."
    $env:PATH += ";$wixPath"
    $type = "msi"
} else {
    Write-Host "WiX Toolset NOT detected. Building portable App Image only."
    Write-Host "To build an MSI/EXE installer, please install WiX Toolset v3: https://wixtoolset.org/releases/"
}

# Run jpackage
$jpackageArgs = @(
  "--name", "LambdaNotes",
  "--input", $inputDir,
  "--main-jar", "lambdanotes-1.0-SNAPSHOT.jar",
  "--main-class", "com.lambdanotes.Launcher",
  "--type", $type,
  "--dest", $outputDir,
  "--app-version", "1.0.0"
)

if ($type -ne "app-image") {
    $jpackageArgs += "--win-menu"
    $jpackageArgs += "--win-shortcut"
}

& $jpackage @jpackageArgs

Write-Host "Build Complete!"
if ($type -eq "app-image") {
    Write-Host "App Image is located in: $outputDir/LambdaNotes"
    Write-Host "You can run it via LambdaNotes.exe inside that folder."
} else {
    Write-Host "Installer is located in: $outputDir"
}
