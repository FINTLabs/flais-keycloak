# -------------------------------------------------------------------------------------------------
# Console header / logo
# -------------------------------------------------------------------------------------------------

function Write-NovariLogo {
    $logoPath = Join-Path $ScriptRoot "novari_logo_primaer.svg"

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
    Write-Host "FINT Entra ID / SCIM Setup" -ForegroundColor Cyan
    Write-Host "Entry menu for creating a FINT Entra Enterprise Application and configuring SCIM provisioning."
    Write-Host ""
    Write-Host "Bootstrap / startup:"
    Write-Host "  - Create non-gallery Enterprise Application"
    Write-Host "  - Optionally connect existing Enterprise Application"
    Write-Host "  - Create Claims Mapping Policy"
    Write-Host "  - Assign Claims Mapping Policy to service principal"
    Write-Host ""
    Write-Host "Configure:"
    Write-Host "  - App Registration settings"
    Write-Host "  - Enterprise Application settings"
    Write-Host "  - SCIM provisioning"
    Write-Host ""
    Write-Host "--------------------------------------------------------------------------------"
}
