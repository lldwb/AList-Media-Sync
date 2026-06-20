$env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2024.3.4.1\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location "C:\Users\Administrator\Documents\GitHub\AList-Media-Sync"
Write-Host "Running mvnw test (full)..."
$result = & .\mvnw.cmd test "-Dskip.frontend=true" 2>&1
$result | Select-Object -Last 80
Write-Host "Exit code: $LASTEXITCODE"
