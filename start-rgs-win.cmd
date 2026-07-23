@echo off

rem @echo off
rem cd /d C:\Projects\RGSCenter\center\target
rem start "" javaw -jar center-0.0.1-SNAPSHOT.jar
rem exit
rem set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"

cd /d C:\Projects\RGSCenter

start "" /B "%JAVA_HOME%\bin\javaw.exe" ^
 -Xms512m ^
 -Xmx2g ^
 -Dfile.encoding=UTF-8 ^
 -jar center-0.0.1-SNAPSHOT.jar

exit