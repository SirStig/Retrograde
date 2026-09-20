@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   Minecraft Mod Template Initializer
echo ========================================
echo.

:: 1. Ask for user inputs
set /p MODID="Enter the Mod ID (lowercase, no spaces, e.g., my_mod): "
set /p MODNAME_READABLE="Enter the readable Mod Name (e.g., My Mod): "
set /p MODNAME_CLASS="Enter the Mod Name without spaces (e.g., MyMod): "
set /p MODGROUP="Enter the package group (lowercase, e.g., com.example) [net.mat0u5]: "
if "%MODGROUP%"=="" set "MODGROUP=net.mat0u5"
set /p MODAUTHOR="Enter your username (e.g., Mat0u5) [Mat0u5]: "
if "%MODAUTHOR%"=="" set "MODAUTHOR=Mat0u5"

echo.
echo [1/8] Renaming .env.template to .env...
if exist ".env.template" (
    ren ".env.template" ".env"
) else (
    echo   - .env.template not found, skipping.
)

echo [2/8] Updating gradle.properties...
if exist "gradle.properties" (
    powershell -Command "(Get-Content gradle.properties) -replace 'mod\.id\s*=.*', 'mod.id=%MODID%' -replace 'mod\.name\s*=.*', 'mod.name=%MODNAME_READABLE%' -replace 'mod\.group\s*=.*', 'mod.group=%MODGROUP%' -creplace 'ModId Name', '%MODNAME_READABLE%' -creplace 'modid', '%MODID%' -creplace 'ModId', '%MODNAME_CLASS%' -creplace 'Mat0u5', '%MODAUTHOR%' | Set-Content gradle.properties"
) else (
    echo   - gradle.properties not found, skipping.
)

echo [3/8] Updating Java packages and files...
set "OLD_PKG=src\main\java\net\mat0u5\modid"
if exist "%OLD_PKG%" (
    if exist "%OLD_PKG%\Main.java" (
        powershell -Command "$content = Get-Content '%OLD_PKG%\Main.java'; $content = $content -creplace 'net\.mat0u5\.modid', '%MODGROUP%.%MODID%' -creplace 'ModId Name', '%MODNAME_READABLE%' -creplace 'modid', '%MODID%' -creplace 'ModId', '%MODNAME_CLASS%'; Set-Content -Path '%OLD_PKG%\Main.java' -Value $content"
    )

    powershell -Command "Get-ChildItem -Path '%OLD_PKG%' -Recurse -Filter *.java | Where-Object { $_.Name -ne 'Main.java' } | ForEach-Object { $content = Get-Content $_.FullName; $content = $content -replace 'net\.mat0u5\.modid', '%MODGROUP%.%MODID%'; Set-Content -Path $_.FullName -Value $content }"

    powershell -Command "$old='%OLD_PKG%'; $groupPath='%MODGROUP%' -replace '\.', [char]92; $newRoot=Join-Path 'src\main\java' $groupPath; $new=Join-Path $newRoot '%MODID%'; if ($old -ne $new) { New-Item -ItemType Directory -Force -Path $newRoot | Out-Null; Move-Item -LiteralPath $old -Destination $new; $root=(Resolve-Path 'src\main\java').Path; $dir=Split-Path $old -Parent; while ((Test-Path $dir) -and ((Resolve-Path $dir).Path -ne $root) -and -not (Get-ChildItem -Force -LiteralPath $dir)) { $p=Split-Path $dir -Parent; Remove-Item -LiteralPath $dir -Force; $dir=$p } }"
) else (
    echo   - Directory %OLD_PKG% not found, skipping.
)

echo [4/8] Updating Resources (Mixins and pack.mcmeta)...
set "RES_DIR=src\main\resources"
if exist "%RES_DIR%" (
    if exist "%RES_DIR%\pack.mcmeta" (
        powershell -Command "(Get-Content '%RES_DIR%\pack.mcmeta') -replace 'ModId', '%MODNAME_READABLE%' | Set-Content '%RES_DIR%\pack.mcmeta'"
    )
    if exist "%RES_DIR%\modid.mixins.json" (
        powershell -Command "(Get-Content '%RES_DIR%\modid.mixins.json') -replace 'net\.mat0u5\.modid', '%MODGROUP%.%MODID%' | Set-Content '%RES_DIR%\modid.mixins.json'"
        ren "%RES_DIR%\modid.mixins.json" "%MODID%.mixins.json"
    )
) else (
    echo   - Directory %RES_DIR% not found, skipping.
)

echo [5/8] Updating stonecutter.gradle.kts...
if exist "stonecutter.gradle.kts" (
    powershell -Command "(Get-Content 'stonecutter.gradle.kts') -creplace 'ModId Name', '%MODNAME_READABLE%' -creplace 'modid', '%MODID%' -creplace 'ModId', '%MODNAME_CLASS%' -creplace 'Mat0u5', '%MODAUTHOR%' | Set-Content 'stonecutter.gradle.kts'"
) else (
    echo   - stonecutter.gradle.kts not found, skipping.
)

echo [6/8] Updating LICENSE...
if exist "LICENSE" (
    powershell -Command "(Get-Content LICENSE) -creplace 'Copyright \(c\) \d{4}', ('Copyright (c) ' + (Get-Date).Year) -creplace 'Mat0u5', '%MODAUTHOR%' | Set-Content LICENSE"
) else (
    echo   - LICENSE not found, skipping.
)

echo [7/8] Removing .git directory...
if exist ".git" (
    rmdir /s /q ".git"
) else (
    echo   - .git directory not found, skipping.
)

echo [8/8] Cleaning up and renaming root directory...
set "SCRIPT_PATH=%~f0"
for %%I in (.) do set "CURRENT_FOLDER=%%~nxI"
cd ..
set "PARENT_DIR=%CD%"

:: Drop the folder lock by moving to the TEMP directory
cd /d "%TEMP%"

:: Create the background cleanup task
set "TEMP_CLEANUP=%TEMP%\mod_cleanup_%RANDOM%.bat"
(
    echo @echo off
    echo timeout /t 3 /nobreak ^>nul
    :: Delete the setup script BEFORE the parent folder gets renamed
    echo del "%SCRIPT_PATH%"
    :: Rename the root folder
    echo ren "%PARENT_DIR%\%CURRENT_FOLDER%" "%MODNAME_CLASS%"
    :: Delete this background script
    echo del "%%~f0"
) > "%TEMP_CLEANUP%"

:: Execute background task and close
start /min "" cmd /c "%TEMP_CLEANUP%"
exit