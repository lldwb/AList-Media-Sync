param($ComputerName = "localhost")

Write-Host "========== System Environment Check Report ==========" -ForegroundColor Cyan

# --- OS Info ---
Write-Host ""
Write-Host "[OS] Operating System" -ForegroundColor Yellow
$osInfo = Get-CimInstance Win32_OperatingSystem
Write-Host "  Name: $($osInfo.Caption)"
Write-Host "  Version: $($osInfo.Version)"
Write-Host "  Architecture: $($osInfo.OSArchitecture)"

# --- Uptime ---
Write-Host ""
Write-Host "[Uptime] System Uptime" -ForegroundColor Yellow
$uptime = (Get-Date) - $osInfo.LastBootUpTime
Write-Host "  Last Boot: $($osInfo.LastBootUpTime.ToString('yyyy-MM-dd HH:mm:ss'))"
Write-Host "  Uptime: $($uptime.Days)d $($uptime.Hours)h $($uptime.Minutes)m"

# --- CPU ---
Write-Host ""
Write-Host "[CPU] Processor" -ForegroundColor Yellow
$cpu = Get-CimInstance Win32_Processor
$cpuLoad = $cpu.LoadPercentage
Write-Host "  Model: $($cpu.Name.Trim())"
Write-Host "  Logical Cores: $($cpu.NumberOfLogicalProcessors)"
Write-Host "  Current Load: ${cpuLoad}%"
if ($cpuLoad -gt 90) {
    Write-Host "  !! WARNING: CPU load is very high!" -ForegroundColor Red
} elseif ($cpuLoad -gt 70) {
    Write-Host "  !! NOTE: CPU load is elevated" -ForegroundColor Yellow
} else {
    Write-Host "  OK: CPU load normal" -ForegroundColor Green
}

# --- Memory ---
Write-Host ""
Write-Host "[Memory] Physical Memory" -ForegroundColor Yellow
$totalMemMB = $osInfo.TotalVisibleMemorySize / 1024
$freeMemMB = $osInfo.FreePhysicalMemory / 1024
$totalGB = [math]::Round($totalMemMB / 1024, 2)
$freeGB = [math]::Round($freeMemMB / 1024, 2)
$usedGB = [math]::Round($totalGB - $freeGB, 2)
$memPct = [math]::Round(($usedGB / $totalGB) * 100, 1)
Write-Host "  Total: ${totalGB} GB"
Write-Host "  Used:  ${usedGB} GB"
Write-Host "  Free:  ${freeGB} GB"
Write-Host "  Usage: ${memPct}%"
if ($memPct -gt 90) {
    Write-Host "  !! WARNING: Memory usage is critical!" -ForegroundColor Red
} elseif ($memPct -gt 80) {
    Write-Host "  !! NOTE: Memory usage is high" -ForegroundColor Yellow
} else {
    Write-Host "  OK: Memory usage normal" -ForegroundColor Green
}

# --- Disk ---
Write-Host ""
Write-Host "[Disk] Disk Space" -ForegroundColor Yellow
Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3" | ForEach-Object {
    $total = [math]::Round($_.Size / 1GB, 2)
    $free = [math]::Round($_.FreeSpace / 1GB, 2)
    $used = [math]::Round(($_.Size - $_.FreeSpace) / 1GB, 2)
    $pct = if ($total -gt 0) { [math]::Round(($used / $total) * 100, 1) } else { 0 }
    Write-Host "  $($_.DeviceID) [$($_.VolumeName)]: Total=${total}GB Used=${used}GB Free=${free}GB Usage=${pct}%"
    if ($pct -gt 95) {
        Write-Host "    !! CRITICAL: Disk nearly full!" -ForegroundColor Red
    } elseif ($pct -gt 90) {
        Write-Host "    !! WARNING: Disk space low!" -ForegroundColor Red
    } elseif ($pct -gt 80) {
        Write-Host "    !! NOTE: Disk space getting low" -ForegroundColor Yellow
    } else {
        Write-Host "    OK: Disk space sufficient" -ForegroundColor Green
    }
}

# --- Network ---
Write-Host ""
Write-Host "[Network] Connectivity" -ForegroundColor Yellow
$pingResult = Test-Connection -ComputerName 8.8.8.8 -Count 2 -Quiet
if ($pingResult) {
    Write-Host "  OK: Internet reachable (8.8.8.8 ping OK)" -ForegroundColor Green
} else {
    Write-Host "  FAIL: Internet unreachable (8.8.8.8 ping failed)" -ForegroundColor Red
}

try {
    $dnsResult = Resolve-DnsName -Name github.com -ErrorAction Stop
    $ips = ($dnsResult | Where-Object { $_.IPAddress } | Select-Object -First 2).IPAddress -join ', '
    Write-Host "  OK: DNS resolution works (github.com -> $ips)" -ForegroundColor Green
} catch {
    Write-Host "  FAIL: DNS resolution failed for github.com" -ForegroundColor Red
}

# Try HTTP
try {
    $httpResult = Invoke-WebRequest -Uri "https://github.com" -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
    Write-Host "  OK: HTTPS to github.com works (HTTP $($httpResult.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "  FAIL: HTTPS to github.com failed - $($_.Exception.Message)" -ForegroundColor Red
}

# --- Key Ports ---
Write-Host ""
Write-Host "[Ports] Key Listening Ports" -ForegroundColor Yellow
$listening = netstat -ano | Select-String "LISTENING"
$keyPorts = @{8080="App HTTP"; 3000="Frontend Dev"; 443="HTTPS"; 3389="RDP"; 445="SMB"; 5244="AList Default"}
foreach ($port in $keyPorts.Keys | Sort-Object) {
    if ($listening -match ":$port ") {
        Write-Host "  OK: Port $port ($($keyPorts[$port])) is listening" -ForegroundColor Green
    }
}

# --- Environment Variables ---
Write-Host ""
Write-Host "[Env] Project-related Environment Variables" -ForegroundColor Yellow
$projectVars = @("JAVA_HOME", "MAVEN_HOME", "NODE_PATH", "GOPATH", "DOCKER_HOST")
foreach ($v in $projectVars | Sort-Object) {
    $val = [Environment]::GetEnvironmentVariable($v)
    if ($val) {
        Write-Host "  OK: $v = $val" -ForegroundColor Green
    } else {
        Write-Host "  MISSING: $v is not set" -ForegroundColor DarkYellow
    }
}

# --- Dev Tools ---
Write-Host ""
Write-Host "[Tools] Development Tools" -ForegroundColor Yellow
$tools = @(
    @{Name="Node.js"; Cmd="node"; Args="--version"},
    @{Name="npm"; Cmd="npm"; Args="--version"},
    @{Name="Python"; Cmd="python"; Args="--version"},
    @{Name="Java"; Cmd="java"; Args="--version"},
    @{Name="javac"; Cmd="javac"; Args="--version"},
    @{Name="Maven"; Cmd="mvn"; Args="--version"},
    @{Name="Git"; Cmd="git"; Args="--version"},
    @{Name="Docker"; Cmd="docker"; Args="--version"},
    @{Name="Go"; Cmd="go"; Args="version"}
)
foreach ($tool in $tools) {
    try {
        $ver = & $tool.Cmd $tool.Args 2>&1 | Select-Object -First 1
        Write-Host "  OK: $($tool.Name) : $ver" -ForegroundColor Green
    } catch {
        Write-Host "  MISSING: $($tool.Name) not found" -ForegroundColor Red
    }
}

# --- System Errors (last 24h) ---
Write-Host ""
Write-Host "[Events] System Errors (last 24h)" -ForegroundColor Yellow
$since = (Get-Date).AddDays(-1)
try {
    $errors = Get-EventLog -LogName System -EntryType Error -After $since -ErrorAction Stop
    if ($errors -and $errors.Count -gt 0) {
        Write-Host "  !! Found $($errors.Count) system errors:"
        $errors | Select-Object -First 10 | ForEach-Object {
            $shortMsg = if ($_.Message.Length -gt 140) { $_.Message.Substring(0, 140) + "..." } else { $_.Message }
            Write-Host "    [$($_.TimeGenerated)] $($_.Source): $shortMsg"
        }
    } else {
        Write-Host "  OK: No system errors in last 24h" -ForegroundColor Green
    }
} catch {
    Write-Host "  SKIP: Cannot access System Event Log ($($_.Exception.Message))" -ForegroundColor DarkYellow
}

# --- .env Config ---
Write-Host ""
Write-Host "[Config] Project .env Configuration" -ForegroundColor Yellow
$envPath = "C:\Users\Administrator\Documents\GitHub\AList-Media-Sync\.env"
if (Test-Path $envPath) {
    $content = Get-Content $envPath -Raw
    $issues = @()
    if ($content -match "ALIST_BASE_URL=https://alist.example.com") {
        $issues += "ALIST_BASE_URL is still the example value (https://alist.example.com)"
    }
    if ($content -match "ALIST_TOKEN=<your-token-here>") {
        $issues += "ALIST_TOKEN is still the placeholder (<your-token-here>)"
    }
    if ($content -notmatch "ALIST_CRYPTO_KEY=.{20,}") {
        $issues += "ALIST_CRYPTO_KEY is missing or too short"
    }
    if ($issues.Count -gt 0) {
        Write-Host "  !! Configuration issues found:"
        foreach ($issue in $issues) {
            Write-Host "    - $issue" -ForegroundColor Yellow
        }
    } else {
        Write-Host "  OK: .env configuration looks valid" -ForegroundColor Green
    }
} else {
    Write-Host "  FAIL: .env file not found!" -ForegroundColor Red
}

# --- Process Stats ---
Write-Host ""
Write-Host "[Processes] Process Statistics" -ForegroundColor Yellow
$nodeCount = (Get-Process -Name "node" -ErrorAction SilentlyContinue).Count
$javaCount = (Get-Process -Name "java*" -ErrorAction SilentlyContinue).Count
Write-Host "  Node.js processes: $nodeCount"
Write-Host "  Java processes: $javaCount"
if ($nodeCount -gt 15) {
    Write-Host "  !! NOTE: High number of Node.js processes (${nodeCount})" -ForegroundColor Yellow
}

# --- Top Memory Processes ---
Write-Host ""
Write-Host "[Processes] Top 10 by Memory (WorkingSet)" -ForegroundColor Yellow
Get-Process | Sort-Object WorkingSet -Descending | Select-Object -First 10 | ForEach-Object {
    $wsMB = [math]::Round($_.WorkingSet / 1MB, 1)
    Write-Host "  $($_.ProcessName) (PID $($_.Id)): ${wsMB} MB"
}

# --- Summary ---
Write-Host ""
Write-Host "========== Check Complete ==========" -ForegroundColor Cyan
Write-Host ""
Write-Host "SUMMARY:" -ForegroundColor White
Write-Host "  - OS: $($osInfo.Caption) $($osInfo.Version) ($($osInfo.OSArchitecture))"
Write-Host "  - CPU: $($cpu.NumberOfLogicalProcessors) cores, ${cpuLoad}% load"
Write-Host "  - Memory: ${totalGB}GB total, ${freeGB}GB free (${memPct}% used)"
Write-Host "  - Uptime: $($uptime.Days)d $($uptime.Hours)h $($uptime.Minutes)m"
