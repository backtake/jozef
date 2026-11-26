@echo off
rem Starts Jozef in the background (no console window) from this folder.
rem Double-click this file, or put a shortcut to it in shell:startup.
setlocal
set "HERE=%~dp0"
set "HERE=%HERE:~0,-1%"
set "JAR=%HERE%\build\libs\jozef-0.0.1.jar"

if not exist "%JAR%" (
  echo Nie znaleziono %JAR%.
  echo Zbuduj Jozefa: gradlew.bat bootJar
  pause
  exit /b 1
)

set "JAVAW="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAW=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVAW if exist "%USERPROFILE%\.jdks\graalvm-jdk-21.0.7\bin\javaw.exe" set "JAVAW=%USERPROFILE%\.jdks\graalvm-jdk-21.0.7\bin\javaw.exe"
if not defined JAVAW for %%J in (javaw.exe) do set "JAVAW=%%~$PATH:J"
if not defined JAVAW (
  echo Nie znaleziono Javy 21 ^(javaw.exe^). Zainstaluj JDK 21 albo ustaw JAVA_HOME.
  pause
  exit /b 1
)

start "Jozef" "%JAVAW%" "-Duser.dir=%HERE%" -Xmx512m -jar "%JAR%"
endlocal
