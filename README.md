# Эмулятор UNIX — вариант 23

Java 17+, консольный эмулятор с виртуальной файловой системой из ZIP.

## Команды
`ls [путь]`, `cd [путь]`, `rev ТЕКСТ|ФАЙЛ`, `tail [-n ЧИСЛО] ФАЙЛ`,
`mkdir ПУТЬ`, `rm ПУТЬ`, `exit`.

`rm` удаляет файлы и пустые каталоги. ZIP не изменяется.
Кавычки объединяют аргументы с пробелами.

## Запуск
Linux/macOS: `sh run.sh --vfs vfs/files.zip --script scripts/stage5.txt`
Windows: `run.bat --vfs vfs\files.zip --script scripts\stage5.txt`
Без параметров запускается пустая VFS. Ошибки скрипта не прерывают его.

## Тесты
`mkdir -p build && javac -d build src/*.java tests/*.java && java -ea -cp build MainTest`

## Примеры
`ls`, `cd docs`, `tail -n 2 notes.txt`, `rev "hello world"`,
`mkdir newdir`, `rm newdir`, `exit`.

## Этапы
1. REPL и парсер; 2. конфигурация и скрипты; 3. ZIP VFS;
4. команды чтения; 5. команды изменения VFS.

Тестовые ZIP-архивы не хранятся в Git. Перед запуском выполните
`python scripts/generate_vfs.py` (или `python3` в Linux).
