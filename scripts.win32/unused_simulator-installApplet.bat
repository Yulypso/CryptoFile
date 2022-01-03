@echo off

call .\setenv.bat

REM apdutool
%JAVA_HOME%\bin\java.exe -noverify -classpath %JC22_HOME%\lib\apdutool.jar;%JC22_HOME%\lib\apduio.jar com.sun.javacard.apdutool.Main -nobanner %OUT%\%PROJECT%\%SIMUSCRIPT%.scr

if errorlevel 1 goto error

goto end

:error
echo ***************
echo    ERROR !
echo ***************
pause
goto end

:end
rem cls
