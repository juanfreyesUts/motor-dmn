# ===================================================================
# Arranca el contenedor dmn-sidecar y abre Swagger UI automaticamente
# cuando el contenedor reporta estado "healthy".
#
# Uso:  doble clic en el acceso directo del Escritorio,
#       o:  powershell -File scripts\start-dmn-sidecar.ps1
# ===================================================================

$ErrorActionPreference = "Stop"

# Ubica la raiz del proyecto (carpeta padre de este script).
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

# URL de Swagger. Se usa 127.0.0.1 (NO localhost): en Windows localhost
# resuelve a IPv6 ::1 y el port-forward de Docker Desktop solo escucha IPv4.
$swaggerUrl = "http://127.0.0.1:8080/swagger-ui/index.html"
$container  = "dmn-sidecar"

Write-Host "==> Levantando el contenedor dmn-sidecar..." -ForegroundColor Cyan
docker compose up -d --build

Write-Host "==> Esperando a que el contenedor este 'healthy'..." -ForegroundColor Cyan
$maxIntentos = 40   # ~3 min (40 x 5s)
$healthy = $false
for ($i = 1; $i -le $maxIntentos; $i++) {
    try {
        $estado = docker inspect --format '{{.State.Health.Status}}' $container 2>$null
    } catch { $estado = "" }

    if ($estado -eq "healthy") { $healthy = $true; break }
    Write-Host ("    intento {0}/{1}: {2}" -f $i, $maxIntentos, $estado)
    Start-Sleep -Seconds 5
}

if (-not $healthy) {
    Write-Host "!! El contenedor no llego a 'healthy' a tiempo. Revisa: docker compose logs" -ForegroundColor Red
    Read-Host "Presiona Enter para salir"
    exit 1
}

Write-Host "==> Contenedor saludable. Abriendo Swagger UI: $swaggerUrl" -ForegroundColor Green
Start-Process $swaggerUrl

Write-Host "==> Listo. El contenedor sigue corriendo en segundo plano." -ForegroundColor Green
Start-Sleep -Seconds 2
