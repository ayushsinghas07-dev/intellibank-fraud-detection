@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Executable for Windows
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set "LOCAL_MVN=%~dp0..\maven\apache-maven-3.9.6\bin\mvn.cmd"
if exist "%LOCAL_MVN%" (
    "%LOCAL_MVN%" %*
) else (
    mvn %*
)
