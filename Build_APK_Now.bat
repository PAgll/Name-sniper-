@echo off
setlocal
echo ======================================================================
echo   1-Click Native Android .APK Builder for Minecraft IGN Sniper
echo ======================================================================
cd /d "%~dp0"

where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [*] Found Gradle! Compiling Minecraft_IGN_Sniper.apk ...
    call gradle assembleDebug
    if exist "app\build\outputs\apk\debug\app-debug.apk" (
        copy /y "app\build\outputs\apk\debug\app-debug.apk" "..\Minecraft_IGN_Sniper.apk" >nul
        echo [SUCCESS] Built ..\Minecraft_IGN_Sniper.apk !
        echo Copy Minecraft_IGN_Sniper.apk to your phone and tap to install!
        pause
        exit /b 0
    )
)

echo.
echo [*] You do not have Gradle/Android Studio installed on this PC yet.
echo [*] FASTEST WAY TO GET THE .APK WITHOUT INSTALLING ANDROID STUDIO:
echo     1. Go to https://github.com/new and create a free repository.
echo     2. Drag and drop the contents of this 'Minecraft_IGN_Sniper_APK' folder into it.
echo     3. Click the 'Actions' tab at the top of your GitHub repository.
echo     4. GitHub's cloud servers will automatically compile 'Minecraft_IGN_Sniper.apk'
echo        for you in ~2 minutes so you can download and install it on your phone!
echo.
pause
