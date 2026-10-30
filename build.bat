@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   Compiling LastPlate Application...
echo ===================================================

:: Detect javac executable
set JAVAC_CMD=javac

if exist "%JAVA_HOME%\bin\javac.exe" (
    set JAVAC_CMD="%JAVA_HOME%\bin\javac.exe"
) else if exist "C:\Program Files\Java\jdk-25.0.3\bin\javac.exe" (
    set JAVAC_CMD="C:\Program Files\Java\jdk-25.0.3\bin\javac.exe"
) else if exist "C:\Program Files\Java\jdk-25\bin\javac.exe" (
    set JAVAC_CMD="C:\Program Files\Java\jdk-25\bin\javac.exe"
) else (
    for /d %%i in ("C:\Program Files\Java\jdk*") do (
        if exist "%%i\bin\javac.exe" set JAVAC_CMD="%%i\bin\javac.exe"
    )
)

if not exist bin (
    mkdir bin
)

echo Using compiler: !JAVAC_CMD!
!JAVAC_CMD! -encoding UTF-8 -cp "lib/*" -d bin src\main\java\com\lastplate\*.java

if %ERRORLEVEL% equ 0 (
    echo [SUCCESS] Compilation finished without errors.
    echo Compiled classes are in the 'bin' directory.
) else (
    echo [ERROR] Compilation failed. Please check Java errors above.
    exit /b %ERRORLEVEL%
)
