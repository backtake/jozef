@echo off
rem Starts Jozef in the background (no console window) from this folder.
rem Double-click this file, or put a shortcut to it in shell:startup.
rem No parenthesised blocks on purpose: a ")" in the install path would break them.
setlocal
set "HERE=%~dp0"
set "HERE=%HERE:~0,-1%"
set "JAR=%HERE%\build\libs\jozef-0.0.1.jar"

if not exist "%JAR%" goto :nojar

set "JAVAW="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAW=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVAW if exist "%USERPROFILE%\.jdks\graalvm-jdk-21.0.7\bin\javaw.exe" set "JAVAW=%USERPROFILE%\.jdks\graalvm-jdk-21.0.7\bin\javaw.exe"
if not defined JAVAW for %%J in (javaw.exe) do set "JAVAW=%%~$PATH:J"
if not defined JAVAW goto :nojava

rem /D sets the real working directory, so data\ and the speech model are found.
start "Jozef" /D "%HERE%" "%JAVAW%" -Xmx512m -jar "%JAR%"
goto :eof

:nojar
echo Nie znaleziono %JAR%.
echo Zbuduj Jozefa: gradlew.bat bootJar
pause
goto :eof

:nojava
echo Nie znaleziono Javy 21 (javaw.exe). Zainstaluj JDK 21 albo ustaw JAVA_HOME.
pause
goto :eof
