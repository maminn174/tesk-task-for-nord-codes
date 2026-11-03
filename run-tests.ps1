
param(
    [Parameter(Mandatory = $true)]
    [string]$JarPath
)

$ErrorActionPreference = "Stop"
$appProcess = $null

try {
    # Определяем полный путь к JAR-файлу приложения
    $JarPath = (Resolve-Path $JarPath).Path

    # Проверяем наличие необходимых команд
    foreach ($command in @("java", "mvn")) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "Не найдена необходимая команда: $command"
        }
    }

    # Проверяем, занят ли порт приложения
    $connection = Test-NetConnection 127.0.0.1 `
        -Port 8080 -InformationLevel Quiet `
        -WarningAction SilentlyContinue

    if ($connection) {
        throw "Порт 8080 уже занят."
    }

    # Запускаем приложение
    Write-Host "Запуск приложения Spring Boot..."

    $appProcess = Start-Process `
        -FilePath "java" `
        -ArgumentList @(
            "-Dsecret=qazWSXedc",
            "-Dmock=http://localhost:8888/",
            "-jar",
            "`"$JarPath`""
        ) `
        -PassThru `
        -NoNewWindow

    # Ожидаем готовности приложения
    $started = $false

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        if ($appProcess.HasExited) {
            throw "Процесс приложения неожиданно завершился."
        }

        $ready = Test-NetConnection 127.0.0.1 `
            -Port 8080 -InformationLevel Quiet `
            -WarningAction SilentlyContinue

        if ($ready) {
            $started = $true
            break
        }

        Start-Sleep -Seconds 1
    }

    if (-not $started) {
        throw "Приложение не запустилось за отведённое время."
    }

    # Запускаем набор тестов
    Write-Host "Запуск автоматизированных тестов..."

    & mvn clean test
    $testExitCode = $LASTEXITCODE

    if ($testExitCode -ne 0) {
        throw "Тесты Maven завершились с кодом ошибки $testExitCode"
    }

    Write-Host "Все тесты завершились успешно."

} catch {
    Write-Error $_.Exception.Message
    exit 1

} finally {
    # Останавливаем приложение
    if ($null -ne $appProcess) {
        Write-Host "Остановка приложения Spring Boot..."

        if (-not $appProcess.HasExited) {
            Stop-Process -Id $appProcess.Id -Force `
                -ErrorAction SilentlyContinue
        }
    }
}
