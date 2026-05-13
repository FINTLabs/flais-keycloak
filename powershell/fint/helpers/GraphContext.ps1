Set-StrictMode -Version Latest

function Connect-ToGraph {
    Write-Host ""
    Write-Host "Microsoft Graph app-only login" -ForegroundColor Cyan
    Write-Host "------------------------------"

    Import-Module Microsoft.Graph.Authentication -ErrorAction Stop

    Disconnect-MgGraph -ErrorAction SilentlyContinue | Out-Null

    $tenantId = Read-RequiredValue "Tenant ID"
    $clientId = Read-RequiredValue "Client ID"
    $clientSecretSecure = Read-Host "Client Secret" -AsSecureString

    if (-not $clientSecretSecure -or $clientSecretSecure.Length -eq 0) {
        throw "Client Secret is required."
    }

    $cred = New-Object System.Management.Automation.PSCredential($clientId, $clientSecretSecure)

    Connect-MgGraph `
        -TenantId $tenantId `
        -ClientSecretCredential $cred `
        -NoWelcome

    $ctx = Get-MgContext

    if (-not $ctx) {
        throw "Graph login failed. No Microsoft Graph context was created."
    }

    Write-Host ""
    Write-Host "Connected to Microsoft Graph." -ForegroundColor Green
    Write-Host "Tenant:    $($ctx.TenantId)"
    Write-Host "Client ID: $($ctx.ClientId)"
    Write-Host "Auth type: $($ctx.AuthType)"
}

function Show-GraphContext {
    $ctx = Get-MgContext

    if (-not $ctx) {
        Write-Host "Not connected to Microsoft Graph." -ForegroundColor Yellow
        return
    }

    Write-Host ""
    Write-Host "Current Graph Context" -ForegroundColor Cyan
    Write-Host "---------------------"
    Write-Host "Tenant ID: $($ctx.TenantId)"
    Write-Host "Client ID: $($ctx.ClientId)"
    Write-Host "Auth Type: $($ctx.AuthType)"
    Write-Host "Scopes:   $($ctx.Scopes | Sort-Object -Unique)"
}
