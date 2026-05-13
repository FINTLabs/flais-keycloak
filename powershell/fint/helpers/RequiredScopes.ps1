Set-StrictMode -Version Latest

function Assert-MgContextHasExactlyRequiredScopes {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$RequiredScopes
    )

    $context = Get-MgContext

    if ($null -eq $context) {
        throw "No Microsoft Graph context found. Run Connect-MgGraph -Scopes '$($RequiredScopes -join ',')' first."
    }

    $actualScopes = @($context.Scopes | Sort-Object -Unique)
    $expectedScopes = @($RequiredScopes | Sort-Object -Unique)

    $missingScopes = @($expectedScopes | Where-Object { $_ -notin $actualScopes })
    $extraScopes = @($actualScopes | Where-Object { $_ -notin $expectedScopes })

    if ($missingScopes.Count -gt 0 -or $extraScopes.Count -gt 0) {
        $message = @(
            "Microsoft Graph context must have exactly the required scopes.",
            "Required scopes: $($expectedScopes -join ', ')",
            "Actual scopes:   $($actualScopes -join ', ')"
        )

        if ($missingScopes.Count -gt 0) {
            $message += "Missing scopes:  $($missingScopes -join ', ')"
        }

        if ($extraScopes.Count -gt 0) {
            $message += "Extra scopes:    $($extraScopes -join ', ')"
        }

        throw ($message -join [Environment]::NewLine)
    }

    Write-Host "Microsoft Graph context has exactly the required scopes: $($expectedScopes -join ', ')"
}
