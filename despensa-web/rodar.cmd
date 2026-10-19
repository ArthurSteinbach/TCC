@echo off
cd /d "%~dp0"
if not defined MONGODB_URI (
    echo A variavel de ambiente MONGODB_URI nao esta definida.
    echo Crie a variavel e abra um novo terminal antes de rodar de novo.
    pause
    exit /b 1
)
echo Abra http://localhost:8080 no navegador. Para encerrar, pressione Ctrl+C.
call mvnw.cmd -q spring-boot:run
