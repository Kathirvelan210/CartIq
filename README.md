# CartIQ team handoff

## Run locally on Windows

Double-click **Start-CartIQ.bat** in the project folder. It reuses running services, builds the Expo web frontend if the local export is missing, and opens [CartIQ](http://localhost:8092/) once the Spring backend is healthy. For a terminal-only readiness check and startup, use `Start-CartIQ.bat -NoBrowser`. The existing local launchers require Node.js, installed project dependencies (`npm ci`), Java 17+ and Maven with the project dependencies cached. The optional recipe service also needs its configured Python virtual environment.

On a cold start, the backend window asks for the verified QuickCommerce dashboard balance to enable shopping. Press Enter to leave live shopping off. Choose whether to enable AI recipes. If enabled, the existing recipe launcher can prompt for the Gemini key separately. Leave the backend window running. The database is the local H2 file under `backend/.runtime`.

## Change the QuickCommerceAPI key used by CartIQ

1. Double-click **Change-QuickCommerce-Key.bat** and paste the key into the masked local prompt. Use the key for the subscription associated with your existing shopping ledger.
2. CartIQ saves it in `backend/.env.local`, encrypted for your Windows account. To apply it, stop the local backend with Ctrl+C in its launcher window and run **Start-CartIQ.bat** again. Enter the current verified dashboard balance to enable live shopping.

The key change updates local storage only. It does not rotate a key at the provider, reset the ledger, or verify provider acceptance.

## Remember the Gemini key on Windows

Double-click **Save-Gemini-Key.bat** and enter your existing Gemini key once. CartIQ encrypts it with Windows DPAPI in the user environment variable `CARTIQ_GEMINI_KEY_DPAPI`. **Start-CartIQ.bat** automatically enables AI recipes on the next cold start and supplies the decrypted `GEMINI_API_KEY` to the private AI service. The same batch file can update the saved key. An explicitly injected `GEMINI_API_KEY` takes precedence.

Source snapshot for development; see documentation/TEAM_SETUP_GUIDE.md. No private MySQL dump or secrets are included. The bundled starter catalogue is source fixture data, not a verified export of the original database. V8 and V9 are pending MySQL review and approval.
