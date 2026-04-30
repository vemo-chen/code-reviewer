@echo off
setlocal

set "ROOT_DIR=%~dp0.."
set "WEB_DIR=%ROOT_DIR%\web-ui"
set "DOCKERFILE_PATH=%ROOT_DIR%\deploy\Dockerfile.web"
if "%OUTPUT_DIR%"=="" set "OUTPUT_DIR=%ROOT_DIR%\dist-images"
if "%IMAGE_REPO%"=="" set "IMAGE_REPO=your-dockerhub-user/code-reviewer-web"

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMddHHmm"') do set "IMAGE_TAG=%%i"

set "IMAGE_NAME=%IMAGE_REPO%:%IMAGE_TAG%"
set "OUTPUT_FILE=%OUTPUT_DIR%\code-reviewer-web-%IMAGE_TAG%.tar"

if not exist "%OUTPUT_DIR%" (
    mkdir "%OUTPUT_DIR%"
)

echo [1/2] Build web image %IMAGE_NAME% ...
docker build -f "%DOCKERFILE_PATH%" -t "%IMAGE_NAME%" "%WEB_DIR%" || goto :error

echo [2/2] Export web image to %OUTPUT_FILE% ...
docker save -o "%OUTPUT_FILE%" "%IMAGE_NAME%" || goto :error

echo.
echo Build completed.
echo Dockerfile: %DOCKERFILE_PATH%
echo Image     : %IMAGE_NAME%
echo File      : %OUTPUT_FILE%
pause
goto :eof

:error
echo.
echo Build failed. Check the error messages above.
pause
exit /b 1
