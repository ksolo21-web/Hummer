@echo off
setlocal
set "APP_HOME=%~dp0"
set "VERSION=9.4.1"
set "SHA256=2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb"
set "CACHE=%APP_HOME%.gradle-bootstrap"
set "ZIP=%CACHE%\gradle-%VERSION%-bin.zip"
set "DIST=%CACHE%\gradle-%VERSION%"
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%CACHE%" mkdir "%CACHE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip';$z='%ZIP%';if(!(Test-Path $z)){Invoke-WebRequest -UseBasicParsing $u -OutFile $z};$h=(Get-FileHash $z -Algorithm SHA256).Hash.ToLower();if($h -ne '%SHA256%'){Remove-Item $z -Force;throw 'Gradle archive checksum mismatch'};Expand-Archive -Path $z -DestinationPath '%CACHE%' -Force"
  if errorlevel 1 exit /b 1
)
call "%DIST%\bin\gradle.bat" %*
exit /b %errorlevel%
