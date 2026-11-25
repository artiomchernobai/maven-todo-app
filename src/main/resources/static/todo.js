const element = (id) => document.getElementById(id);
const filters = element("filters");
const taskForm = element("task-form");
const categoryForm = element("category-form");
const deleteForm = element("delete-form");
const statusLabels = { TODO: "К выполнению", IN_PROGRESS: "В работе", DONE: "Выполнено" };
const priorityLabels = { LOW: "Низкий", MEDIUM: "Средний", HIGH: "Высокий" };
const priorityRank = { LOW: 0, MEDIUM: 1, HIGH: 2 };
const categoryPalette = ["#67a68a", "#d99a45", "#8d91c9", "#ce7988", "#53a1bc", "#a18d59"];
const quickDateButtons = [...element("quick-dates").querySelectorAll("[data-range]")];
const exactDate = filters.elements.namedItem("dueDate");
const dateFormat = new Intl.DateTimeFormat("ru", { dateStyle: "medium", timeZone: "UTC" });
const countFormat = new Intl.NumberFormat("ru");

let categories = [];
let allTasks = [];
let hasLoaded = false;
let selectedCategory = "";
let dueRange = "";
let filterRequest;
let refreshVersion = 0;
let emptyAction;

async function api(path, { method = "GET", body, signal } = {}) {
    const response = await fetch(`api/${path}`, {
        method,
        signal,
        cache: "no-store",
        headers: body === undefined ? undefined : { "Content-Type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
    }).catch((error) => {
        if (signal?.aborted) throw error;
        throw new Error("Нет связи с сервером. Проверьте, что приложение запущено.");
    });

    if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        const messages = {
            "A category with this name already exists.": "Категория с таким названием уже есть.",
            "Move or delete the tasks in this category first.": "Сначала перенесите или удалите задачи из этой категории.",
        };
        let detail = messages[problem.detail] ?? problem.detail ?? "Запрос не выполнен.";
        if (problem.errors) {
            const fields = {
                title: "Название", name: "Название категории", categoryId: "Категория",
                priority: "Приоритет", description: "Описание", color: "Цвет категории",
            };
            detail = Object.entries(problem.errors)
                .map(([field, message]) => `${fields[field] ?? field}: ${message}`)
                .join(". ");
        }
        throw new Error(`HTTP ${response.status}. ${detail}`);
    }

    return response.status === 204 ? undefined : response.json();
}

function notice(message, kind = "success") {
    const banner = element("notice");
    banner.textContent = message;
    banner.dataset.kind = kind;
    banner.hidden = false;
}

function categoryColor(category) {
    return category.color ?? categoryPalette[(category.id - 1) % categoryPalette.length];
}

function updateSidebar() {
    const rows = categories.map((category) => {
        const row = element("category-row").content.firstElementChild.cloneNode(true);
        const link = row.querySelector(".category-link");
        link.dataset.categoryId = category.id;
        link.title = category.name;
        row.querySelector(".category-dot").style.setProperty("--category-color", categoryColor(category));
        row.querySelector(".category-name").textContent = category.name;
        link.addEventListener("click", () => selectCategory(String(category.id)));

        const edit = row.querySelector(".edit-category");
        edit.setAttribute("aria-label", `Изменить категорию «${category.name}»`);
        edit.addEventListener("click", () => useButton(edit, async () => {
            openCategory(await api(`categories/${category.id}`));
        }));

        const remove = row.querySelector(".remove-category");
        remove.setAttribute("aria-label", `Удалить категорию «${category.name}»`);
        remove.addEventListener("click", () => openDelete("categories", category.id, category.name));
        return row;
    });
    element("category-list").replaceChildren(...rows);
    element("no-categories").hidden = categories.length !== 0;
    element("add-task").disabled = categories.length === 0;
    updateCategorySelection();
}

function updateCategorySelection() {
    element("all-categories").setAttribute("aria-pressed", String(!selectedCategory));
    for (const button of element("category-list").querySelectorAll(".category-link")) {
        button.setAttribute("aria-pressed", String(button.dataset.categoryId === selectedCategory));
    }

    const category = categories.find(({ id }) => String(id) === selectedCategory);
    element("list-heading").textContent = category?.name ?? "Все задачи";
}

function selectCategory(id) {
    selectedCategory = id;
    updateCategorySelection();
    void loadTasks();
}

function updateSummary() {
    const counts = { TODO: 0, IN_PROGRESS: 0, DONE: 0 };
    for (const task of allTasks) {
        counts[task.status]++;
    }
    element("total-count").textContent = countFormat.format(allTasks.length);
    element("todo-count").textContent = countFormat.format(counts.TODO);
    element("progress-count").textContent = countFormat.format(counts.IN_PROGRESS);
    element("done-count").textContent = countFormat.format(counts.DONE);
}

function showEmpty(title, description, actionLabel, action) {
    element("table-wrap").hidden = true;
    element("empty-state").hidden = false;
    element("empty-title").textContent = title;
    element("empty-description").textContent = description;
    element("empty-description").hidden = !description;
    element("empty-action").textContent = actionLabel;
    element("empty-action").hidden = !action;
    emptyAction = action;
}

function showLoadError(error) {
    element("task-results").setAttribute("aria-busy", "false");
    element("visible-count").hidden = true;
    showEmpty("Не удалось загрузить задачи", error.message, "Повторить", refresh);
}

function renderTasks(tasks, filtered) {
    const counter = element("visible-count");
    counter.textContent = countFormat.format(tasks.length);
    counter.hidden = false;

    const categoriesById = new Map(categories.map((category) => [category.id, category]));
    element("task-list").replaceChildren(...tasks.map((task) => taskRow(task, categoriesById)));
    element("table-wrap").hidden = tasks.length === 0;
    element("empty-state").hidden = tasks.length > 0;

    if (tasks.length > 0) return;
    if (filtered) {
        showEmpty("Задачи не найдены", "Измените или сбросьте фильтры.", "Сбросить фильтры", resetFilters);
    } else if (categories.length === 0) {
        showEmpty("Задач пока нет", "Создайте категорию, чтобы добавить первую задачу.", "Создать категорию", () => openCategory());
    } else {
        showEmpty("Задач пока нет", "Добавьте первую задачу.", "Создать задачу", () => openTask());
    }
}

function taskRow(task, categoriesById) {
    const row = element("task-row").content.firstElementChild.cloneNode(true);
    row.querySelector(".task-title").textContent = task.title;
    const category = categoriesById.get(task.categoryId);
    row.querySelector(".task-category-name").textContent = category?.name ?? `Категория №${task.categoryId}`;
    if (category) {
        row.querySelector(".category-dot").style.setProperty("--category-color", categoryColor(category));
    }
    const description = row.querySelector(".task-description");
    description.textContent = task.description ?? "";
    description.hidden = !task.description;
    row.querySelector(".task-date").textContent = task.dueDate
        ? dateFormat.format(new Date(`${task.dueDate}T00:00:00Z`))
        : "—";

    const status = row.querySelector(".task-status");
    status.textContent = statusLabels[task.status];
    status.dataset.value = task.status;
    const priority = row.querySelector(".task-priority");
    priority.textContent = priorityLabels[task.priority];
    priority.dataset.value = task.priority;

    const complete = row.querySelector(".complete-task");
    complete.setAttribute("aria-label", `Завершить задачу «${task.title}»`);
    complete.disabled = task.status === "DONE";
    complete.addEventListener("click", () => useButton(complete, async () => {
        await api(`tasks/${task.id}/complete`, { method: "PATCH" });
        await refresh();
        notice("Задача выполнена.");
    }));

    const edit = row.querySelector(".edit-task");
    edit.setAttribute("aria-label", `Изменить задачу «${task.title}»`);
    edit.addEventListener("click", () => useButton(edit, async () => {
        openTask(await api(`tasks/${task.id}`));
    }));

    const remove = row.querySelector(".remove-task");
    remove.setAttribute("aria-label", `Удалить задачу «${task.title}»`);
    remove.addEventListener("click", () => openDelete("tasks", task.id, task.title));
    return row;
}

async function useButton(button, action) {
    button.disabled = true;
    try {
        await action();
    } catch (error) {
        notice(error.message, "error");
    } finally {
        button.disabled = false;
    }
}

function setDueRange(range) {
    dueRange = range;
    for (const button of quickDateButtons) {
        button.setAttribute("aria-pressed", String(button.dataset.range === dueRange));
    }
}

function localDateKey(date) {
    return [date.getFullYear(), date.getMonth() + 1, date.getDate()]
        .map((part, index) => String(part).padStart(index === 0 ? 4 : 2, "0"))
        .join("-");
}

function filterByDueRange(tasks) {
    if (!dueRange) return tasks;

    const now = new Date();
    const today = localDateKey(now);
    if (dueRange === "today") return tasks.filter((task) => task.dueDate === today);
    if (dueRange === "overdue") {
        return tasks.filter((task) => task.dueDate && task.dueDate < today && task.status !== "DONE");
    }

    const monday = new Date(now);
    monday.setDate(now.getDate() - (now.getDay() + 6) % 7);
    const sunday = new Date(monday);
    sunday.setDate(monday.getDate() + 6);
    const start = localDateKey(monday);
    const end = localDateKey(sunday);
    return tasks.filter((task) => task.dueDate && task.dueDate >= start && task.dueDate <= end);
}

function sortTasks(tasks) {
    const sort = element("task-sort").value;
    return [...tasks].sort((first, second) => {
        if (sort === "dueDate") {
            if (first.dueDate && !second.dueDate) return -1;
            if (!first.dueDate && second.dueDate) return 1;
            if (first.dueDate !== second.dueDate) return first.dueDate < second.dueDate ? -1 : 1;
        }
        if (sort === "priority" && first.priority !== second.priority) {
            return priorityRank[second.priority] - priorityRank[first.priority];
        }
        return second.id - first.id;
    });
}

async function loadTasks() {
    if (!hasLoaded) return;
    filterRequest?.abort();
    const request = new AbortController();
    filterRequest = request;

    const params = new URLSearchParams();
    for (const [field, value] of new FormData(filters)) {
        if (value) params.set(field, value);
    }
    if (selectedCategory) params.set("categoryId", selectedCategory);

    const filtered = params.size > 0 || Boolean(dueRange);
    element("reset-filters").disabled = !filtered;
    element("task-results").setAttribute("aria-busy", "true");
    try {
        const tasks = params.size ? await api(`tasks?${params}`, { signal: request.signal }) : allTasks;
        if (!request.signal.aborted) renderTasks(sortTasks(filterByDueRange(tasks)), filtered);
    } catch (error) {
        if (!request.signal.aborted) showLoadError(error);
    } finally {
        if (!request.signal.aborted) element("task-results").setAttribute("aria-busy", "false");
    }
}

async function refresh() {
    const version = ++refreshVersion;
    try {
        const [nextCategories, nextTasks] = await Promise.all([api("categories"), api("tasks")]);
        if (version !== refreshVersion) return;
        categories = nextCategories;
        allTasks = nextTasks;
        hasLoaded = true;
        if (!categories.some(({ id }) => String(id) === selectedCategory)) selectedCategory = "";
        updateSidebar();
        updateSummary();
        await loadTasks();
    } catch (error) {
        if (version === refreshVersion) {
            filterRequest?.abort();
            showLoadError(error);
        }
    }
}

function resetFilters() {
    filters.reset();
    setDueRange("");
    selectCategory("");
}

function openTask(task) {
    taskForm.reset();
    taskForm.dataset.id = task?.id ?? "";
    element("task-dialog-title").textContent = task ? "Редактирование задачи" : "Новая задача";

    const categoryField = taskForm.elements.namedItem("categoryId");
    categoryField.replaceChildren(...categories.map(({ id, name }) => new Option(name, String(id))));
    categoryField.value = String(task?.categoryId ?? (selectedCategory || categories[0]?.id || ""));
    if (task) {
        for (const name of ["title", "description", "status", "priority", "dueDate"]) {
            taskForm.elements.namedItem(name).value = task[name] ?? "";
        }
    }
    openDialog(taskForm);
}

function openCategory(category) {
    categoryForm.reset();
    categoryForm.dataset.id = category?.id ?? "";
    categoryForm.elements.namedItem("name").value = category?.name ?? "";
    categoryForm.elements.namedItem("color").value = category
        ? categoryColor(category)
        : categoryPalette[categories.length % categoryPalette.length];
    element("category-dialog-title").textContent = category ? "Редактирование категории" : "Новая категория";
    openDialog(categoryForm);
}

function openDelete(resource, id, name) {
    const task = resource === "tasks";
    deleteForm.dataset.resource = resource;
    deleteForm.dataset.id = id;
    element("delete-title").textContent = task ? "Удалить задачу?" : "Удалить категорию?";
    element("delete-description").textContent = `«${name}» будет удалена.`;
    openDialog(deleteForm);
}

function openDialog(form) {
    form.querySelector(".form-error").hidden = true;
    form.closest("dialog").showModal();
}

function configureDialog(form, save) {
    const dialog = form.closest("dialog");
    const submit = form.querySelector("[type=submit]");
    for (const button of form.querySelectorAll("[data-close]")) {
        button.addEventListener("click", () => {
            if (!submit.disabled) dialog.close();
        });
    }
    dialog.addEventListener("cancel", (event) => {
        if (submit.disabled) event.preventDefault();
    });
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (submit.disabled) return;
        const errorBox = form.querySelector(".form-error");
        errorBox.hidden = true;
        submit.disabled = true;
        try {
            const message = await save(new FormData(form));
            dialog.close();
            notice(message);
            void refresh();
        } catch (error) {
            errorBox.textContent = error.message;
            errorBox.hidden = false;
        } finally {
            submit.disabled = false;
        }
    });
}

configureDialog(taskForm, async (data) => {
    const id = taskForm.dataset.id;
    await api(id ? `tasks/${id}` : "tasks", {
        method: id ? "PUT" : "POST",
        body: {
            title: data.get("title").trim(),
            description: data.get("description").trim() || null,
            categoryId: Number(data.get("categoryId")),
            dueDate: data.get("dueDate") || null,
            status: data.get("status"),
            priority: data.get("priority"),
        },
    });
    return id ? "Изменения задачи сохранены." : "Задача создана.";
});

configureDialog(categoryForm, async (data) => {
    const id = categoryForm.dataset.id;
    await api(id ? `categories/${id}` : "categories", {
        method: id ? "PUT" : "POST",
        body: { name: data.get("name").trim(), color: data.get("color") },
    });
    return id ? "Изменения категории сохранены." : "Категория создана.";
});

configureDialog(deleteForm, async () => {
    const { resource, id } = deleteForm.dataset;
    await api(`${resource}/${id}`, { method: "DELETE" });
    return resource === "tasks" ? "Задача удалена." : "Категория удалена.";
});

element("add-task").addEventListener("click", () => openTask());
element("add-category").addEventListener("click", () => openCategory());
element("all-categories").addEventListener("click", () => selectCategory(""));
element("empty-action").addEventListener("click", () => emptyAction?.());
element("reset-filters").addEventListener("click", resetFilters);
for (const button of quickDateButtons) {
    button.addEventListener("click", () => {
        const range = button.dataset.range;
        setDueRange(dueRange === range ? "" : range);
        if (dueRange) exactDate.value = "";
        void loadTasks();
    });
}
element("task-sort").addEventListener("change", () => void loadTasks());
filters.addEventListener("change", (event) => {
    if (event.target === exactDate && exactDate.value) setDueRange("");
    void loadTasks();
});
filters.addEventListener("submit", (event) => event.preventDefault());
void refresh();
