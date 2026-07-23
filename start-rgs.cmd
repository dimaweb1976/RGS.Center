@echo off

rem set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot

set PATH=%JAVA_HOME%\bin;%PATH%

cd /d C:\Projects\RGSCenter

java ^
-Xms512m ^
-Xmx2g ^
-Dfile.encoding=UTF-8 ^
-jar center-0.0.1-SNAPSHOT.jar