@echo off

call .\setenv.bat

%JC22_HOME%\bin\cref.exe -nomeminfo -nobanner -o %OUT%\%PROJECT%\eeprom
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
