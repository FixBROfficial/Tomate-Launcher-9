@echo off
setlocal
cd /d "%~dp0"

set "JAR_TOOL=jar"
if exist "C:\Program Files\Java\jdk-21.0.10\bin\jar.exe" set "JAR_TOOL=C:\Program Files\Java\jdk-21.0.10\bin\jar.exe"

if not exist out mkdir out
echo Compilando TomateStarter...
javac -encoding UTF-8 -source 1.8 -target 1.8 -d out src\main\java\br\com\simplelauncher\TomateStarter.java src\main\java\br\com\simplelauncher\Json.java src\main\java\br\com\simplelauncher\Java8.java
if errorlevel 1 (
    echo Erro ao compilar o TomateStarter!
    pause
    exit /b %errorlevel%
)

echo Empacotando TomateStarter.jar...
"%JAR_TOOL%" --create --file TomateStarter.jar --main-class br.com.simplelauncher.TomateStarter -C out br/com/simplelauncher/TomateStarter.class -C out br/com/simplelauncher/TomateStarter$RemoteFile.class -C out br/com/simplelauncher/Json.class -C out br/com/simplelauncher/Java8.class
if errorlevel 1 (
    echo Erro ao gerar TomateStarter.jar!
    pause
    exit /b %errorlevel%
)

echo ========================================================
echo TomateStarter.jar portatil gerado com sucesso!
echo Arquivo para distribuir: %cd%\TomateStarter.jar
echo.
echo As pessoas podem colocar esse .jar em qualquer pasta!
echo Os arquivos do Launcher ficarao em:
echo   %%APPDATA%%\TomateLauncher9\Launcher
echo ========================================================
pause
