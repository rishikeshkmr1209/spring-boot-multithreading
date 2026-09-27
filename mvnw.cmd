@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script for Spring Boot Virtual Threads Project
@REM ----------------------------------------------------------------------------
@IF "%DEBUG%"=="" @ECHO OFF
@SETLOCAL ENABLEEXTENSIONS ENABLEDELAYEDEXPANSION

SET "BASE_DIR=%~dp0"
SET "LOCAL_MVN=%BASE_DIR%.tools\apache-maven-3.9.9\bin\mvn.cmd"

IF EXIST "%LOCAL_MVN%" (
  CALL "%LOCAL_MVN%" %*
  EXIT /B %ERRORLEVEL%
)

WHERE mvn >nul 2>nul
IF %ERRORLEVEL% EQU 0 (
  CALL mvn %*
  EXIT /B %ERRORLEVEL%
)

ECHO Error: Maven not found. Please install Maven or verify .tools\apache-maven-3.9.9 exists.
EXIT /B 1
