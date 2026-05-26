Set-StrictMode -Version Latest

function Write-NovariLogo {
    $logoPath = Join-Path $PSScriptRoot "novari_logo_primaer.svg"

    if ((Get-Command chafa -ErrorAction SilentlyContinue) -and (Test-Path $logoPath)) {
        & chafa `
            --symbols=block `
            --colors=full `
            --size=80x12 `
            $logoPath

        return
    }

    $esc = [char]27

    $novariOrange = "$esc[38;2;247;102;80m"
    $reset = "$esc[0m"

    $logo = @'
   ▄▄█████▄▄      ███▄     ███▄        ███▀    ▄▄█████▄▄       ███   ▄▄█   ███
 ▄████▀▀▀▀████    ▀▀████▄   ███▄      ████   ▄███▀▀▀▀▀███▄     ███▄█████   ███
▄███▀      ▀███      ▀███▄   ███     ▄███   ███▀       ▀███    █████▀▀     ███
███          ███       ███   ▀███    ███    ███         ███▄   ███         ███
███       ███          ███    ▀███  ███▀    ███         ████   ███         ███
███       ▀███▄      ▄███▀     ███▄▄██▀     ███▄       ▄████   ███         ███
███        ▀████▄▄▄▄████▀       ██████       ▀████▄▄▄███████   ███         ███
███          ▀▀██████▀▀          ████          ▀▀██████▀▀██▀   ███         ███
'@

    Write-Host "$novariOrange$logo$reset"
}

function Show-Header {
    Clear-Host

    Write-NovariLogo

    Write-Host ""
    Write-Host "FINT Entra ID Setup for Keycloak" -ForegroundColor Cyan
    Write-Host "Script for creating a FINT Entra Enterprise Application and configuration."
    Write-Host "--------------------------------------------------------------------------------"
}
