@echo off
if "%~1"=="" goto error
java "%~dp0src\PyJaConverter.java" "%~1"
if errorlevel 1 exit /b 1
javac -cp "%~dp1." -sourcepath "%~dp1." "%~dpn1.java"
if errorlevel 1 exit /b 1
goto end
:error
echo Error: Input file path is required.
echo Usage: pyjac ^<FileName.pyja^>
exit /b 1
:end
