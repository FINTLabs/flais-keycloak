# Initial Setup

## Create the temporary PowerShell app registration

1. Create a new app registration for the initial PowerShell setup.
2. Add the following API permission:

   * `Application.ReadWrite.OwnedBy`
3. Grant admin consent for the permission.
4. Create a client secret for the app registration.
5. Start the setup script.

## Create and configure the Enterprise Application

1. Create the Enterprise Application.
2. Configure the application owner.
3. Restart the setup script.

## Configure the final app registration in Entra

1. Open the newly created app registration in Microsoft Entra.
2. Add the following API permissions:

   * `Application.ReadWrite.OwnedBy`
   * `Synchronization.ReadWrite.All`
3. Grant admin consent for the permissions.
4. Create the initial client secret.

## Complete the setup

1. Connect to the same app registration during login.
2. Run the required setup steps.
3. Create a new client secret.
4. Delete the old client secret.
5. Save the new client secret securely.


## Grant admin consent for delegated permissions

1. Open the app registration
2. Grant admin consent for all permissions.

## Test login

1. Test the login.

## Clean up

1. Delete the temporary PowerShell app registration created at the beginning.
