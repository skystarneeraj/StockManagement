@echo off
rem Starts the Stock Management System on http://localhost:8080
cd /d "%~dp0"
rem Use any installed Temurin JDK 21 (the folder name changes with each update).
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%~fD"
call "%~dp0mvnw.cmd" spring-boot:run %*
