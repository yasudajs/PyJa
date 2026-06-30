@echo off
setlocal enabledelayedexpansion

set "PATHS_0=.vscode"
set "PATHS_1=.antigravity-ide"
set "PATHS_2=.cursor"
set "PATHS_3=.windsurf"
set "PATHS_4=.vscode-oss"

set "UNINSTALLED_COUNT=0"

for /L %%i in (0,1,4) do (
    set "FOLDER_NAME="
    if %%i equ 0 set "FOLDER_NAME=%PATHS_0%"
    if %%i equ 1 set "FOLDER_NAME=%PATHS_1%"
    if %%i equ 2 set "FOLDER_NAME=%PATHS_2%"
    if %%i equ 3 set "FOLDER_NAME=%PATHS_3%"
    if %%i equ 4 set "FOLDER_NAME=%PATHS_4%"
    
    if defined FOLDER_NAME (
        set "TARGET_DIR=%USERPROFILE%\!FOLDER_NAME!\extensions\pyja-language"
        
        if exist "!TARGET_DIR!" (
            echo Uninstalling from %USERPROFILE%\!FOLDER_NAME!\extensions...
            rmdir /s /q "!TARGET_DIR!"
            if !errorlevel! equ 0 (
                set /a UNINSTALLED_COUNT+=1
            )
        )
    )
)

echo:
if !UNINSTALLED_COUNT! gtr 0 goto success
goto fail

:success
echo PyJa extension uninstalled successfully from !UNINSTALLED_COUNT! IDE(s).
echo Please restart your IDE to apply changes.
goto end

:fail
echo PyJa extension was not found in any IDE extensions directory.
goto end

:end
