$env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2024.3.4.1\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location "C:\Users\Administrator\Documents\GitHub\AList-Media-Sync"
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "Java version:"
& "$env:JAVA_HOME\bin\javac.exe" -version 2>&1
Write-Host "Running mvnw compile..."
& .\mvnw.cmd compile "-Dskip.frontend=true" 2>&1
