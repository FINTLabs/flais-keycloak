Set-StrictMode -Version Latest

function Show-Menu {
    Write-Host ""
    Write-Host "Menu" -ForegroundColor Cyan
    Write-Host "----"

    if (-not $script:LastEnterpriseApplicationResult) {
        Write-Host "1. Create Enterprise Application"
    }

    Write-Host "2. Create and assign Claims Mapping Policy"
    Write-Host "3. Configure Enterprise Application + App Registration settings"
    Write-Host "4. Configure FINT SCIM Provisioning"
    Write-Host "5. Show Active Graph Context"
    Write-Host "6. Show Current Enterprise Application"
    Write-Host "0. Exit"
    Write-Host ""
}

function Invoke-MenuChoice {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Choice
    )

    switch ($Choice) {
        "1" {
            if ($script:LastEnterpriseApplicationResult) {
                Write-Host ""
                Write-Host "An Enterprise Application is already connected in this session." -ForegroundColor Yellow
                Write-Host "Create Enterprise Application is hidden because an existing application is populated."
                return
            }

            $script:LastEnterpriseApplicationResult = Invoke-CreateEnterpriseApplication
        }

        "2" {
            Invoke-CreateClaimsMappingPolicy `
                -ExistingApplicationResult $script:LastEnterpriseApplicationResult |
            Out-Null
        }

        "3" {
            Invoke-ConfigureEnterpriseApplication `
                -ExistingApplicationResult $script:LastEnterpriseApplicationResult |
            Out-Null
        }

        "4" {
            $servicePrincipalObjectId = $null

            if ($script:LastEnterpriseApplicationResult) {
                $servicePrincipalObjectId = $script:LastEnterpriseApplicationResult.ServicePrincipalObjectId
            }

            Invoke-ConfigureScimProvisioning `
                -ExistingServicePrincipalObjectId $servicePrincipalObjectId |
            Out-Null
        }

        "5" {
            Show-GraphContext
        }

        "6" {
            Show-CurrentEnterpriseApplication
        }

        "0" {
            Write-Host "Exiting."
        }

        default {
            if ($script:LastEnterpriseApplicationResult) {
                Write-Host "Invalid option. Please choose 0, 2, 3, 4, 5, or 6." -ForegroundColor Yellow
            }
            else {
                Write-Host "Invalid option. Please choose 0, 1, 2, 3, 4, 5, or 6." -ForegroundColor Yellow
            }
        }
    }
}

function Start-Menu {
    Show-Header
    Connect-ToGraph

    $script:LastEnterpriseApplicationResult = Get-ExistingEnterpriseApplication

    do {
        Show-Header
        Show-Menu

        $choice = Read-Host "Choose an option"

        Invoke-MenuChoice -Choice $choice

        if ($choice -ne "0") {
            Write-Host ""
            Read-Host "Press Enter to continue"
        }
    } while ($choice -ne "0")
}
