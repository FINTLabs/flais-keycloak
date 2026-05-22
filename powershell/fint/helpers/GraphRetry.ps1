Set-StrictMode -Version Latest

function Get-GraphExceptionDetails {
    param(
        [Parameter(Mandatory)]
        [System.Management.Automation.ErrorRecord]$ErrorRecord
    )

    $details = [ordered]@{
        ExceptionType = $ErrorRecord.Exception.GetType().FullName
        Message       = $ErrorRecord.Exception.Message
        StatusCode    = $null
        ErrorCode     = $null
        ErrorMessage  = $null
        InnerError    = $null
        RawBody       = $null
    }

    try {
        if ($ErrorRecord.Exception.Response -and $ErrorRecord.Exception.Response.StatusCode) {
            $details.StatusCode = [int]$ErrorRecord.Exception.Response.StatusCode
        }
    }
    catch {
    }

    try {
        if ($ErrorRecord.ErrorDetails -and $ErrorRecord.ErrorDetails.Message) {
            $details.RawBody = $ErrorRecord.ErrorDetails.Message
        }
    }
    catch {
    }

    if (-not $details.RawBody) {
        try {
            $response = $ErrorRecord.Exception.Response

            if ($response -and $response.GetResponseStream) {
                $stream = $response.GetResponseStream()
                $reader = [System.IO.StreamReader]::new($stream)
                $details.RawBody = $reader.ReadToEnd()
            }
        }
        catch {
        }
    }

    if ($details.RawBody) {
        try {
            $json = $details.RawBody | ConvertFrom-Json -ErrorAction Stop

            if ($json.error) {
                $details.ErrorCode = $json.error.code
                $details.ErrorMessage = $json.error.message

                if ($json.error.innerError) {
                    $details.InnerError = ($json.error.innerError | ConvertTo-Json -Depth 20)
                }
            }
            elseif ($json.code -or $json.message) {
                $details.ErrorCode = $json.code
                $details.ErrorMessage = $json.message
            }
        }
        catch {
        }
    }

    return [pscustomobject]$details
}

function Write-GraphExceptionDetails {
    param(
        [Parameter(Mandatory)]
        [System.Management.Automation.ErrorRecord]$ErrorRecord,

        [Parameter(Mandatory)]
        [string]$Method,

        [Parameter(Mandatory)]
        [string]$Uri,

        [Parameter()]
        [string]$BodyJson = $null,

        [Parameter()]
        [int]$Attempt,

        [Parameter()]
        [int]$MaxAttempts
    )

    $details = Get-GraphExceptionDetails -ErrorRecord $ErrorRecord

    Write-Warning "Attempt $Attempt/$MaxAttempts failed ($Method $Uri)"
    Write-Warning "Exception: $($details.ExceptionType)"
    Write-Warning "Message: $($details.Message)"

    if ($null -ne $details.StatusCode) {
        Write-Warning "HTTP status: $($details.StatusCode)"
    }

    if ($details.ErrorCode) {
        Write-Warning "Graph error code: $($details.ErrorCode)"
    }

    if ($details.ErrorMessage) {
        Write-Warning "Graph error message: $($details.ErrorMessage)"
    }

    if ($details.InnerError) {
        Write-Warning "Graph innerError: $($details.InnerError)"
    }

    if ($details.RawBody) {
        Write-Warning "Raw Graph response body:"
        Write-Warning $details.RawBody
    }
}

function Invoke-GraphWithRetry {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("GET", "PUT", "POST", "PATCH")]
        [string]$Method,

        [Parameter(Mandatory)]
        [string]$Uri,

        [Parameter()]
        [string]$BodyJson = $null,

        [int]$MaxAttempts = 12,

        [int]$InitialDelaySeconds = 5,

        [switch]$NoRetryOnBadRequest
    )

    $delay = $InitialDelaySeconds

    for ($i = 1; $i -le $MaxAttempts; $i++) {
        try {
            $headers = @{
                Accept = "application/json"
            }

            if ($BodyJson) {
                return Invoke-MgGraphRequest `
                    -Method $Method `
                    -Uri $Uri `
                    -Body $BodyJson `
                    -ContentType "application/json" `
                    -Headers $headers `
                    -ErrorAction Stop
            }

            return Invoke-MgGraphRequest `
                -Method $Method `
                -Uri $Uri `
                -Headers $headers `
                -ErrorAction Stop
        }
        catch {
            Write-GraphExceptionDetails `
                -ErrorRecord $_ `
                -Method $Method `
                -Uri $Uri `
                -BodyJson $BodyJson `
                -Attempt $i `
                -MaxAttempts $MaxAttempts

            $statusCode = $null

            try {
                if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                    $statusCode = [int]$_.Exception.Response.StatusCode
                }
            }
            catch {
                # Ignore.
            }

            if ($NoRetryOnBadRequest -and $statusCode -eq 400) {
                throw
            }

            if ($i -eq $MaxAttempts) {
                throw
            }

            Start-Sleep -Seconds $delay
            $delay = [Math]::Min($delay * 2, 60)
        }
    }
}
