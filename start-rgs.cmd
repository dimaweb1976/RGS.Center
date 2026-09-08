@echo off
setlocal

rem set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot

cd /d C:\Projects\RGSCenter

if not exist ".env" (
    echo Required environment file not found: C:\Projects\RGSCenter\.env
    exit /b 1
)

rem Load non-commented KEY=VALUE entries used by the Spring datasource.
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do (
    if not "%%A"=="" set "%%A=%%B"
)

rem Configure only the BCrypt hash; never pass the plain master password.
set "RGS_MASTER_PASSWORD_BCRYPT=$2a$10$KfGwblxRgJE1eQe1c7kDOeWw9MuZxuJxufHQDiv2bBgQsnVqGjXzO"

set "PATH=%JAVA_HOME%\bin;%PATH%"

java ^
-Xms512m ^
-Xmx2g ^
-Dfile.encoding=UTF-8 ^
-jar center-0.0.1-SNAPSHOT.jar
