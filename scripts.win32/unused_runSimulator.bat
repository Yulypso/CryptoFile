@echo off

call .\setenv.bat

%JC22_HOME%\bin\cref.exe -nobanner -nomeminfo -i %OUT%\%PROJECT%\eeprom -o %OUT%\%PROJECT%\eeprom
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
