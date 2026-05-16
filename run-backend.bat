@echo off
setlocal
cd /d "%~dp0"

if exist "%~dp0.env.local" (
  echo Carregando variaveis de .env.local...
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%~dp0.env.local") do set "%%A=%%B"
)

if "%JWT_SECRET%"=="" (
  set "JWT_SECRET=local-dev-secret-change-before-production-123456"
)

if "%SPRING_PROFILES_ACTIVE%"=="" (
  set "SPRING_PROFILES_ACTIVE=supabase"
)

if /i "%SPRING_PROFILES_ACTIVE%"=="supabase" (
  if "%DATABASE_URL%"=="" (
    set "DATABASE_URL=jdbc:postgresql://aws-1-us-west-1.pooler.supabase.com:5432/postgres?sslmode=require"
  )

  if "%DATABASE_USERNAME%"=="" (
    set "DATABASE_USERNAME=postgres.hjigxbjeeecuehlbxxny"
  )

  if "%DATABASE_PASSWORD%"=="" (
    echo.
    echo DATABASE_PASSWORD nao foi encontrada.
    set /p "DATABASE_PASSWORD=Digite a senha do banco Supabase: "
  )

  if "%SUPABASE_KEY%"=="" (
    echo.
    echo SUPABASE_KEY nao foi encontrada. O backend sobe, mas upload real pode falhar.
    set "SUPABASE_KEY=dev-only-missing-supabase-key"
  )
)

echo Iniciando backend Spring Boot...
echo Perfil ativo: %SPRING_PROFILES_ACTIVE%
echo Banco: %DATABASE_URL%
call .\mvnw.cmd spring-boot:run
echo.
echo Backend finalizado. Se houve erro, veja as mensagens acima.
pause
