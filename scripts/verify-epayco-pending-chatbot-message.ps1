$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$controllerPath = Join-Path $repoRoot 'src/main/java/com/Aplication/HARO/Controller/EpaycoController.java'

if (-not (Test-Path $controllerPath)) {
    throw "No se encontro el archivo: $controllerPath"
}

$content = [System.IO.File]::ReadAllText(
    $controllerPath,
    [System.Text.UTF8Encoding]::new($false)
)

$checks = @(
    @{
        Name = 'Webhook pending dispara proceso PENDING'
        Pattern = 'processNonApprovedPayment\(xDocumento, xRefPayco, xCodResponse, estado, xReason, PaymentUserStatus\.PENDING\);'
    },
    @{
        Name = 'Existe builder para mensaje de estado de pago'
        Pattern = 'private String buildPaymentStatusNotificationMessage\(ChatbotMatriculaProceso proceso,'
    },
    @{
        Name = 'response sync tambien fuerza pagos no aprobados'
        Pattern = 'syncNonApprovedPaymentToFlow\('
    },
    @{
        Name = 'Mensaje pendiente usa estado PENDIENTE'
        Pattern = 'Tu pago aparece como \*PENDIENTE\*'
    },
    @{
        Name = 'Mensaje pendiente menciona envio automatico de contratos'
        Pattern = 'firmar los contratos\.'
    },
    @{
        Name = 'Mensaje agrega prompt de asesor'
        Pattern = 'msg\.append\(ADVISOR_PROMPT\);'
    },
    @{
        Name = 'Envio real usa WhatsApp text'
        Pattern = 'waService\.sendTextMessage\(phone, msg\);'
    }
)

$results = foreach ($check in $checks) {
    [pscustomobject]@{
        Check = $check.Name
        Ok = [regex]::IsMatch($content, $check.Pattern)
    }
}

$results | Format-Table -AutoSize

$failed = $results | Where-Object { -not $_.Ok }
if ($failed) {
    throw ('Fallaron validaciones: ' + (($failed.Check) -join ', '))
}

Write-Host ''
Write-Host 'Validacion completada: el flujo PENDING de ePayco tiene disparo y mensaje de chatbot configurados.' -ForegroundColor Green
