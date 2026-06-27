@echo off
if "%~1"=="" goto error
java %*
goto end
:error
echo Error: Class name is required.
echo Usage: pyja ^<ClassName^> [args...]
exit /b 1
:end
