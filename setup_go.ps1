$zipPath = "C:\Users\Administrator\AppData\Local\Temp\go1.26.4.windows-amd64.zip"
$destPath = "C:\Users\Administrator\go"

Write-Host "Source: $zipPath"
Write-Host "Destination: $destPath"

if (-not (Test-Path $zipPath)) {
    Write-Host "ERROR: Zip file not found" -ForegroundColor Red
    exit 1
}

Expand-Archive -Path $zipPath -DestinationPath $destPath -Force
Write-Host "Extracted to $destPath"

$goExe = "$destPath\go\bin\go.exe"
if (Test-Path $goExe) {
    $ver = & $goExe version
    Write-Host "Go version: $ver" -ForegroundColor Green

    # Set environment
    [Environment]::SetEnvironmentVariable("GOROOT", "$destPath\go", "User")
    [Environment]::SetEnvironmentVariable("GOPATH", "C:\Users\Administrator\go-projects", "User")
    Write-Host "GOROOT set to $destPath\go"
    Write-Host "GOPATH set to C:\Users\Administrator\go-projects"

    # Add to PATH
    $oldPath = [Environment]::GetEnvironmentVariable("PATH", "User")
    $goBin = "$destPath\go\bin"
    if ($oldPath -notlike "*$goBin*") {
        [Environment]::SetEnvironmentVariable("PATH", "$goBin;$oldPath", "User")
        Write-Host "Go bin added to user PATH"
    } else {
        Write-Host "Go bin already in PATH"
    }
    Write-Host "SUCCESS" -ForegroundColor Green
} else {
    Write-Host "FAILED: go.exe not found after extract" -ForegroundColor Red
}
