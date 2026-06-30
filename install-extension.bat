@echo off
setlocal enabledelayedexpansion

set "PATHS_0=.vscode"
set "PATHS_1=.antigravity-ide"
set "PATHS_2=.cursor"
set "PATHS_3=.windsurf"
set "PATHS_4=.vscode-oss"

set "INSTALLED_COUNT=0"

for /L %%i in (0,1,4) do (
    set "FOLDER_NAME="
    if %%i equ 0 set "FOLDER_NAME=%PATHS_0%"
    if %%i equ 1 set "FOLDER_NAME=%PATHS_1%"
    if %%i equ 2 set "FOLDER_NAME=%PATHS_2%"
    if %%i equ 3 set "FOLDER_NAME=%PATHS_3%"
    if %%i equ 4 set "FOLDER_NAME=%PATHS_4%"
    
    if defined FOLDER_NAME (
        set "TARGET_PARENT=%USERPROFILE%\!FOLDER_NAME!"
        set "TARGET_DIR=!TARGET_PARENT!\extensions\pyja-language"
        
        if exist "!TARGET_PARENT!" (
            echo Installing to !TARGET_PARENT!\extensions...
            if not exist "!TARGET_PARENT!\extensions" mkdir "!TARGET_PARENT!\extensions"
            if exist "!TARGET_DIR!" rmdir /s /q "!TARGET_DIR!"
            xcopy "%~dp0editor\vscode\pyja-extension" "!TARGET_DIR!" /E /I /Q >nul
            if !errorlevel! equ 0 (
                set /a INSTALLED_COUNT+=1
            )
        )
    )
)

echo:
if !INSTALLED_COUNT! gtr 0 goto success
goto fail

:success
echo PyJa extension installed successfully for !INSTALLED_COUNT! IDE(s).
echo Please restart your IDE to apply changes.
goto end

:fail
echo No compatible IDE directories found.
echo Creating extension folder in .vscode by default.
set "DEFAULT_DIR=%USERPROFILE%\.vscode\extensions\pyja-language"
if exist "!DEFAULT_DIR!" rmdir /s /q "!DEFAULT_DIR!"
if not exist "%USERPROFILE%\.vscode\extensions" mkdir "%USERPROFILE%\.vscode\extensions"
xcopy "%~dp0editor\vscode\pyja-extension" "!DEFAULT_DIR!" /E /I /Q >nul
goto end

:end
