@echo off
rem Starts the Stock Management System on http://localhost:8080
cd /d "%~dp0"
if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
call mvnw.cmd spring-boot:run %*
