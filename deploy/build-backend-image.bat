@echo off
setlocal

set "ROOT_DIR=%~dp0.."
set "DOCKERFILE_PATH=%ROOT_DIR%\deploy\Dockerfile.backend"
if "%OUTPUT_DIR%"=="" set "OUTPUT_DIR=%ROOT_DIR%\dist-images"
if "%IMAGE_REPO%"=="" set "IMAGE_REPO=your-dockerhub-user/code-reviewer-backend"
if "%MAVEN_CMD%"=="" set "MAVEN_CMD=mvn"
if not "%JAVA_HOME%"=="" set "PATH=%JAVA_HOME%\bin;%PATH%"
set "JAR_PATH=%ROOT_DIR%\target\code-reviewer-0.0.1-SNAPSHOT.jar"

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMddHHmm"') do set "IMAGE_TAG=%%i"

set "IMAGE_NAME=%IMAGE_REPO%:%IMAGE_TAG%"
set "OUTPUT_FILE=%OUTPUT_DIR%\code-reviewer-backend-%IMAGE_TAG%.tar"

if not exist "%OUTPUT_DIR%" (
    mkdir "%OUTPUT_DIR%"
)

echo [1/3] Build backend jar locally...
cd /d "%ROOT_DIR%" || goto :error
call "%MAVEN_CMD%" -Dmaven.test.skip=true clean package || goto :error

if not exist "%JAR_PATH%" (
    echo.
    echo Backend jar not found: %JAR_PATH%
    goto :error
)

echo [2/3] Build backend image %IMAGE_NAME% ...
docker build -f "%DOCKERFILE_PATH%" -t "%IMAGE_NAME%" "%ROOT_DIR%" || goto :error

echo [3/3] Export backend image to %OUTPUT_FILE% ...
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
