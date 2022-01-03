@echo off

call .\setenv.bat

set CLASSPATH=%JC22_HOME%\lib\apduio.jar
set CLASSPATH=%CLASSPATH%;%OCF_HOME%\lib\base-core.jar
set CLASSPATH=%CLASSPATH%;%OCF_HOME%\lib\base-opt.jar
set CLASSPATH=%CLASSPATH%;%MISC%\%PCSC_WRAPPER%\lib\%PCSC_WRAPPER%.jar
set CLASSPATH=%CLASSPATH%;%MISC%\%APDUIO_TERM%\lib\%APDUIO_TERM%.jar
set CLASSPATH=%CLASSPATH%;%MISC%\bcprov-jdk15on-150.jar


%JAVA_HOME%\bin\java.exe -Djava.library.path=. -classpath %OUT%\%PROJECT%\;%CLASSPATH% %PKGCLIENT%.%CLIENT%
if errorlevel 1 goto error

pause
goto end

:error
echo **************
echo    ERROR !
echo **************
pause
goto end

:end
cls
