# Runs TraceFlow locally on Windows without Docker: backend with the embedded H2 database ("local" profile) and the
# Angular dev server with an /api proxy. Requires Java 25 and Node.js 22+ (Maven is provided by the wrapper).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

Write-Host "==> Starting backend on http://localhost:8080 (profile: local)"
$backend = Start-Process -FilePath "$root\backend\mvnw.cmd" -ArgumentList "-q", "spring-boot:run", "-Dspring-boot.run.profiles=local" `
    -WorkingDirectory "$root\backend" -PassThru -NoNewWindow

try {
    Write-Host "==> Waiting for the backend to become healthy"
    for ($i = 0; $i -lt 90; $i++) {
        try { Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health | Out-Null; break } catch { Start-Sleep -Seconds 2 }
    }
    Write-Host "==> Starting frontend on http://localhost:4200"
    Set-Location "$root\frontend"
    if (-not (Test-Path node_modules)) { npm ci }
    npx ng serve --port 4200
}
finally {
    Write-Host "==> Stopping backend"
    Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue
}
