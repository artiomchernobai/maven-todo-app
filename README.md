# maven-todo-app

REST API для управления задачами и категориями. Проект использует Java 21, Spring Boot, Spring Data JPA и H2.

## Запуск

Требуются JDK 21 и Maven 3.9.

```bash
mvn spring-boot:run
```

API доступен по адресу `http://localhost:8080/api`. H2 хранит данные в `data/todo.mv.db` относительно рабочего каталога. Переменная `TODO_DB_URL` позволяет задать другой JDBC URL для H2.

```bash
mvn clean verify
java -jar target/maven-todo-app-0.1.0-SNAPSHOT.jar
```

Первая команда запускает интеграционные тесты и собирает JAR. Тесты используют отдельную H2 в памяти.

## API

| Метод | Путь | Действие |
| --- | --- | --- |
| GET | `/api/tasks` | Список задач с необязательными фильтрами |
| POST | `/api/tasks` | Создать задачу |
| GET | `/api/tasks/{id}` | Получить задачу |
| PUT | `/api/tasks/{id}` | Изменить задачу |
| PATCH | `/api/tasks/{id}/complete` | Установить статус `DONE` |
| DELETE | `/api/tasks/{id}` | Удалить задачу |
| GET | `/api/categories` | Список категорий |
| POST | `/api/categories` | Создать категорию |
| GET | `/api/categories/{id}` | Получить категорию |
| PUT | `/api/categories/{id}` | Переименовать категорию |
| DELETE | `/api/categories/{id}` | Удалить категорию, если в ней нет задач |

У задачи обязательны непустой заголовок `title`, приоритет `priority` и идентификатор существующей категории `categoryId`. Приоритеты: `LOW`, `MEDIUM`, `HIGH`. Статусы: `TODO`, `IN_PROGRESS`, `DONE`. При создании без статуса используется `TODO`, а при редактировании без статуса сохраняется текущее значение. Поля `description` и `dueDate` необязательны. При редактировании их можно очистить, передав `null` или опустив поле. Срок задаётся в формате `YYYY-MM-DD`.

Список задач принимает фильтры `status`, `priority`, `categoryId` и `dueDate` (точная дата). Если передано несколько фильтров, задача должна соответствовать всем.

Создание возвращает `201 Created` и заголовок `Location`, удаление возвращает `204 No Content`. Ошибки запроса возвращают `400 Bad Request`, отсутствующие ресурсы — `404 Not Found`. Повторяющееся имя категории или попытка удалить категорию с задачами возвращают `409 Conflict`.

## Пример

Создайте категорию и подставьте её `id` из ответа вместо `1` во второй команде.

```bash
curl -X POST http://localhost:8080/api/categories \
  -H 'Content-Type: application/json' \
  -d '{"name":"Учёба"}'

curl -X POST http://localhost:8080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"Сдать проект","priority":"HIGH","dueDate":"2026-12-01","categoryId":1}'

curl 'http://localhost:8080/api/tasks?status=TODO&priority=HIGH&dueDate=2026-12-01'
```

Замените `1` в следующей команде на `id` созданной задачи.

```bash
curl -X PATCH http://localhost:8080/api/tasks/1/complete
```
