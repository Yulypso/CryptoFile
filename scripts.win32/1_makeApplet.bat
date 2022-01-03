@echo off

call .\setenv.bat

IF NOT EXIST %OUT%\%PROJECT% MD %OUT%\%PROJECT% 

echo Compilating...
%JAVA_HOME%\bin\javac.exe -source 1.2 -target 1.2 -g -classpath %JC22_API% -d %OUT%\%PROJECT%\ %SRC%\%PROJECT%\%PKGAPPLET%\%APPLET%.java
if errorlevel 1 goto error
echo %APPLET%.class compiled: OK
echo .

echo Converting...
%JAVA_HOME%\bin\java.exe -classpath %JC22_HOME%\lib\converter.jar;%JC22_HOME%\lib\offcardverifier.jar com.sun.javacard.converter.Converter -nobanner -classdir %OUT%\%PROJECT%\ -exportpath %JC_EXP% -d %OUT%\%PROJECT%\ -out EXP JCA CAP -applet %AIDAPPLET% %PKGAPPLET%.%APPLET% %PKGAPPLET% %AIDPACKAGE% %PKGVERSION%
if errorlevel 1 goto error
echo %APPLET%.cap converted: OK
echo .
copy %OUT%\%PROJECT%\%PKGAPPLET%\javacard\%PKGAPPLET%.cap %OUT%\%PROJECT%\

rem build a .JAR file
REM %JAVA_HOME%\bin\java.exe -classpath ..\jcasm.jar;%CLASSPATH% com.sun.javacard.jcasm.cap.Main -o %OUT%\%PROJECT%\applet\javacard\applet.jar %OUT%\%PROJECT%\applet\javacard\applet.jca
REM if errorlevel 1 goto error
REM echo %APPLET%.jar converted: OK

echo Scripting...
%JAVA_HOME%\bin\java.exe -classpath %JC22_HOME%\lib\scriptgen.jar com.sun.javacard.scriptgen.Main -nobanner -o %OUT%\%PROJECT%\%APPLET%.scr %OUT%\%PROJECT%\%PKGAPPLET%\javacard\%PKGAPPLET%.cap
if errorlevel 1 goto error
echo %APPLET%.scr created: OK
echo .

echo Completing script...
copy /A /Y %MISC%\Header.scr + %OUT%\%PROJECT%\%APPLET%.scr + %MISC%\Install.scr + %MISC%\Footer.scr %OUT%\%PROJECT%\%SIMUSCRIPT%.scr
del %OUT%\%PROJECT%\%APPLET%.scr
echo %SIMUSCRIPT%.scr created: OK
echo .

if errorlevel 1 goto error

goto end

:error
echo ***************
echo    ERROR !
echo ***************
pause
goto end

:end
cls
