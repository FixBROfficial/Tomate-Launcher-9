@echo off
setlocal
cd /d "%~dp0"

set "JAR_TOOL=jar"
if exist "C:\Program Files\Java\jdk-21.0.10\bin\jar.exe" set "JAR_TOOL=C:\Program Files\Java\jdk-21.0.10\bin\jar.exe"

if not exist out mkdir out
javac -encoding UTF-8 -source 1.8 -target 1.8 -d out src\main\java\br\com\simplelauncher\*.java
if errorlevel 1 exit /b %errorlevel%

"%JAR_TOOL%" --create --file TomateLauncher8.jar --main-class br.com.simplelauncher.LauncherApp -C out .
if errorlevel 1 exit /b %errorlevel%

echo Build Java 8 pronta:
echo %cd%\TomateLauncher8.jar
pause
