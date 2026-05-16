@echo off
setlocal
cd /d "%~dp0frontend"
echo Iniciando frontend React...
call npm run dev
if errorlevel 1 (
  echo.
  echo O frontend encerrou com erro.
  pause
)
