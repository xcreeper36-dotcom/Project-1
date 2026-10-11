@echo off
if not exist build mkdir build
javac -encoding UTF-8 -d build src\*.java
if errorlevel 1 exit /b 1
java -cp build Main %*
