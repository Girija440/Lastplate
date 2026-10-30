@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   Starting LastPlate Server...
echo ===================================================

:: Detect java executable
set JAVA_CMD=java

if exist "%JAVA_HOME%\bin\java.exe" (
    set JAVA_CMD="%JAVA_HOME%\bin\java.exe"
) else if exist "C:\Program Files\Java\jdk-25.0.3\bin\java.exe" (
    set JAVA_CMD="C:\Program Files\Java\jdk-25.0.3\bin\java.exe"
) else if exist "C:\Program Files\Java\jdk-25\bin\java.exe" (
    set JAVA_CMD="C:\Program Files\Java\jdk-25\bin\java.exe"
) else (
    for /d %%i in ("C:\Program Files\Java\jdk*") do (
        if exist "%%i\bin\java.exe" set JAVA_CMD="%%i\bin\java.exe"
    )
)

echo Using runtime: !JAVA_CMD!
!JAVA_CMD! -cp "bin;lib/*" com.lastplate.Main
