$jdkHome = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"

# Set JAVA_HOME
[Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkHome, "User")
Write-Host "JAVA_HOME = $jdkHome"

# Add to PATH if not present
$oldPath = [Environment]::GetEnvironmentVariable("PATH", "User")
$jdkBin = "$jdkHome\bin"
if ($oldPath -notlike "*$jdkBin*") {
    [Environment]::SetEnvironmentVariable("PATH", "$jdkBin;$oldPath", "User")
    Write-Host "Added JDK bin to user PATH"
} else {
    Write-Host "JDK bin already in user PATH"
}
Write-Host "Done"
