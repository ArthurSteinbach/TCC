@echo off
cd /d "%~dp0"
call mvnw.cmd -q compile exec:java "-Dexec.mainClass=br.com.unisinos.App"
