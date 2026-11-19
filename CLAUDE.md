# email-tool

## A change is not done until the running jar is updated

The app the user sees (http://localhost:17432, tray icon, scheduled triage) runs
`backend\target\backend-0.0.1-SNAPSHOT.jar`, started from the Windows Startup shortcut.
Compiling, running tests or `mvnw spring-boot:run` do NOT update it: an old jar keeps serving
and silently ignores new request parameters (this happened: the `since` date filter was in the
source for days while the jar still showed months-old mail).

After any backend or frontend change, deploy it:

```powershell
.\scripts\dev\build-app.ps1
```

It stops the running backend (Windows locks the jar, so packaging fails otherwise), builds UI +
jar with tests, and starts the app on the new jar. It refuses to run while
`results\nightly\running.txt` exists (a triage run is in progress). Then check the change against
the live app on port 17432, not only in tests.
