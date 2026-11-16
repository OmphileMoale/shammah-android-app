@echo off
setlocal EnableExtensions
title Shammah - phone access (ngrok tunnel)
cd /d "%~dp0"
echo ==========================================================
echo   SHAMMAH - lets the app on your phone reach this PC
echo   Keep this window OPEN while people use the app.
echo ==========================================================
echo.

rem ---- 1. Find ngrok.exe ------------------------------------------------
set "NGROK="
if exist "%~dp0ngrok.exe" set "NGROK=%~dp0ngrok.exe"
if not defined NGROK if exist "%USERPROFILE%\Downloads\ngrok.exe" set "NGROK=%USERPROFILE%\Downloads\ngrok.exe"
if not defined NGROK for /f "delims=" %%i in ('where ngrok 2^>nul') do if not defined NGROK set "NGROK=%%i"
if not defined NGROK (
  echo [X] ngrok.exe not found.
  echo     1. Download "ngrok for Windows" from https://ngrok.com/download
  echo     2. Unzip it and put ngrok.exe in THIS folder:
  echo        %~dp0
  echo     3. Run this file again.
  pause
  exit /b 1
)
echo [OK] ngrok found: %NGROK%

rem ---- 2. Authtoken (only the first time) -------------------------------
if not exist "%LOCALAPPDATA%\ngrok\ngrok.yml" (
  echo.
  echo First time only: open https://dashboard.ngrok.com/get-started/your-authtoken
  echo Copy YOUR authtoken and paste it here ^(right-click pastes^), then press Enter.
  set /p "NGTOKEN=Authtoken: "
  call "%NGROK%" config add-authtoken %%NGTOKEN%%
  set "NGTOKEN="
)

rem ---- 3. Your free ngrok address (only the first time) -----------------
set "DOMAIN="
if exist "%~dp0shammah_domain.txt" set /p DOMAIN=<"%~dp0shammah_domain.txt"
if not defined DOMAIN (
  echo.
  echo Open https://dashboard.ngrok.com/domains and copy your free domain
  echo ^(it looks like  something-something.ngrok-free.app^).
  set /p "DOMAIN=Domain: "
)
set "DOMAIN=%DOMAIN:https://=%"
set "DOMAIN=%DOMAIN:http://=%"
set "DOMAIN=%DOMAIN:/=%"
set "DOMAIN=%DOMAIN: =%"
> "%~dp0shammah_domain.txt" echo %DOMAIN%

rem ---- 4. Check XAMPP is serving the church site on port 8099 -----------
curl -s -o nul -w "%%{http_code}" http://localhost:8099/shammah-urban-church/api/index.php > "%TEMP%\shammah_check.txt" 2>nul
set /p CODE=<"%TEMP%\shammah_check.txt"
if not "%CODE%"=="200" (
  echo.
  echo [X] The church site is not answering on port 8099 ^(got "%CODE%"^).
  echo     Open the XAMPP Control Panel, STOP then START Apache, and START MySQL.
  echo     If Apache will not start, tell Claude what the XAMPP log says.
  pause
  exit /b 1
)
echo [OK] Church site and database are running.
echo.
echo ==========================================================
echo   In the app: long-press SIGN IN and type
echo       %DOMAIN%
echo   then press SAVE ^& TEST.
echo   Website from anywhere: https://%DOMAIN%/shammah-urban-church/
echo ==========================================================
echo.
"%NGROK%" http 8099 --url=https://%DOMAIN%
echo.
echo The tunnel stopped. If it said the domain is wrong, delete shammah_domain.txt and run again.
pause
