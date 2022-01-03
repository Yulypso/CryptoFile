@echo off

call .\setenv.bat

echo mode_201 > %OUT%\config.txt
echo gemXpressoPro >> %OUT%\config.txt
echo enable_trace >> %OUT%\config.txt
echo establish_context >> %OUT%\config.txt
echo card_connect >> %OUT%\config.txt
echo select -AID %CARDSECURITYDOMAINGXPPROR32E64PK% >> %OUT%\config.txt
echo open_sc -security %SECURITYGXPPRO% -keyind 0 -keyver %KEYVERSIONGXPPROR32E64PK% -key %CARDKEYGXPPRO% >> %OUT%\config.txt
echo install -file %OUT%\%PROJECT%\%PKGAPPLET%\javacard\%PKGAPPLET%.cap -sdAID %CARDSECURITYDOMAINGXPPROR32E64PK% -nvCodeLimit 4000 >> %OUT%\config.txt
echo card_disconnect >> %OUT%\config.txt
echo release_context >> %OUT%\config.txt

%GPSHELL%\gpshell.exe < %OUT%\config.txt
del %OUT%\config.txt
