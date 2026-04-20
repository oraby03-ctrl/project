@echo off
title Multiplayer Game Platform

echo ==============================================
echo  Multiplayer Game Platform
echo ==============================================

REM ── 1. Compile ────────────────────────────────
echo [1/2] Compiling...
call mvn -q -DskipTests compile
if %ERRORLEVEL% neq 0 (
    echo.
    echo  ERROR: Compilation failed. See output above.
    pause
    exit /b 1
)
echo       Done.

REM ── 2. Run server ─────────────────────────────
echo [2/2] Starting server...
echo       Open http://localhost:8080 in your browser.
echo       Press Ctrl+C to stop the server.
echo ==============================================
call mvn -q exec:java -Dexec.mainClass="com.webapp.server.presentation.ServerMain"

pause
