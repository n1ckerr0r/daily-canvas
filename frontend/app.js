const runtimeConfig = window.DAILY_CANVAS_CONFIG || {};
const apiBaseUrl = (runtimeConfig.apiBaseUrl || `${window.location.origin}/api/v1`).replace(/\/$/, "");

const state = {
  view: "overview",
  query: "",
  activeTag: "",
  payload: null,
  detail: null,
  registerMode: false,
};

const elements = {
  apiBaseUrl: document.querySelector("#api-base-url"),
  pageTitle: document.querySelector("#page-title"),
  searchInput: document.querySelector("#search-input"),
  refreshButton: document.querySelector("#refresh-button"),
  heroCard: document.querySelector("#hero-card"),
  statusCard: document.querySelector("#status-card"),
  galleryStrip: document.querySelector("#gallery-strip"),
  galleryGrid: document.querySelector("#gallery-grid"),
  favoritesGrid: document.querySelector("#favorites-grid"),
  settingsForm: document.querySelector("#settings-form"),
  detailCard: document.querySelector("#detail-card"),
  tagFilters: document.querySelector("#tag-filters"),
  navButtons: Array.from(document.querySelectorAll(".nav-button")),
  views: Array.from(document.querySelectorAll(".view")),
  authScreen: document.querySelector("#auth-screen"),
  authForm: document.querySelector("#auth-form"),
  authTitle: document.querySelector("#auth-title"),
  authCopy: document.querySelector("#auth-copy"),
  authEmail: document.querySelector("#auth-email"),
  authPassword: document.querySelector("#auth-password"),
  authError: document.querySelector("#auth-error"),
  authSubmit: document.querySelector("#auth-submit"),
  authToggle: document.querySelector("#auth-toggle"),
  appError: document.querySelector("#app-error"),
};

elements.apiBaseUrl.textContent = apiBaseUrl;

const apiErrorMessage = (status, code) => {
  const messages = {
    invalid_email: "Введите корректный адрес электронной почты.",
    invalid_credentials: "Неверный email или пароль.",
    email_taken: "Аккаунт с таким email уже существует.",
    unauthorized: "Сессия истекла. Войдите снова.",
    invalid_token: "Сессия истекла. Войдите снова.",
    not_found: "Запрошенные данные не найдены.",
    bad_request: "Проверьте введённые данные.",
    internal_error: "Сервер временно не может выполнить запрос. Попробуйте позже.",
  };
  if (messages[code]) return messages[code];
  if (status >= 500) return "Сервер временно недоступен. Попробуйте позже.";
  return "Не удалось выполнить запрос. Попробуйте ещё раз.";
};

const request = async (path, options = {}) => {
  const token = sessionStorage.getItem("dailycanvas_access_token");
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(options.headers || {}) },
    ...options,
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(apiErrorMessage(response.status, payload.code));
  return payload;
};

const showAppError = (error) => {
  elements.appError.textContent = error?.message || "Произошла ошибка. Попробуйте ещё раз.";
  elements.appError.classList.add("visible");
};
const clearAppError = () => {
  elements.appError.textContent = "";
  elements.appError.classList.remove("visible");
};

const setAuthMode = (registerMode) => {
  state.registerMode = registerMode;
  elements.authTitle.textContent = registerMode ? "Создайте аккаунт" : "Войдите в коллекцию";
  elements.authCopy.textContent = registerMode ? "Избранные и настройки будут храниться в вашем аккаунте." : "Ваши избранные и настройки будут сохранены в аккаунте.";
  elements.authSubmit.textContent = registerMode ? "Зарегистрироваться" : "Войти";
  elements.authToggle.textContent = registerMode ? "Уже есть аккаунт? Войти" : "Нет аккаунта? Зарегистрироваться";
  elements.authError.textContent = "";
};

const showAuth = (message = "") => { elements.authScreen.classList.add("visible"); elements.authError.textContent = message; };
const hideAuth = () => elements.authScreen.classList.remove("visible");

elements.authToggle.addEventListener("click", () => setAuthMode(!state.registerMode));
elements.authForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const email = elements.authEmail.value.trim();
  const password = elements.authPassword.value;
  elements.authError.textContent = "";
  if (!elements.authEmail.checkValidity()) {
    elements.authError.textContent = "Введите корректный email, например name@example.com";
    return;
  }
  try {
    const response = await fetch(`${apiBaseUrl}/auth/${state.registerMode ? "register" : "login"}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password }) });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(apiErrorMessage(response.status, payload.code));
    sessionStorage.setItem("dailycanvas_access_token", payload.accessToken);
    sessionStorage.setItem("dailycanvas_refresh_token", payload.refreshToken);
    hideAuth();
    await refresh();
  } catch (error) { elements.authError.textContent = error.message || "Не удалось авторизоваться"; }
});

const render = () => {
  syncViews();
  renderOverview();
  renderGallery();
  renderFavorites();
  renderSettings();
  renderDetail();
};

const syncViews = () => {
  const titles = {
    overview: "Сегодня",
    gallery: "Галерея",
    favorites: "Избранное",
    settings: "Настройки",
  };

  elements.pageTitle.textContent = titles[state.view];
  elements.navButtons.forEach((button) => {
    button.classList.toggle("active", button.dataset.view === state.view);
  });
  elements.views.forEach((view) => {
    view.classList.toggle("active", view.id === `${state.view}-view`);
  });
};

const image = (url, alt) => `<img src="${url}" alt="${alt}" loading="lazy" />`;

const renderOverview = () => {
  const payload = state.payload;
  if (!payload) return;

  const art = payload.main.todayArtwork;
  elements.heroCard.classList.remove("loading");
  elements.heroCard.innerHTML = `
    <div class="hero-visual">${image(art.imageUrl, art.title)}</div>
    <div class="hero-meta">
      <div>
        <p class="eyebrow">Картина дня</p>
        <h3>${art.title}</h3>
        <p class="hero-text">${art.artist.name} • ${art.year}</p>
      </div>
      <button class="favorite-button ${art.isFavorite ? "active" : ""}" data-favorite="${art.id}">
        ${art.isFavorite ? "♥" : "♡"}
      </button>
    </div>
    <p class="hero-text">${art.description}</p>
    <div class="tag-row">${art.tags.map((tag) => `<button class="chip" data-tag="${tag.id}">${tag.name}</button>`).join("")}</div>
  `;

  elements.statusCard.classList.remove("loading");
  elements.statusCard.innerHTML = `
    <div class="status-item">
      <p class="eyebrow">Уведомления</p>
      <h4>${payload.status.message}</h4>
      <p class="meta-copy">Напоминания: ${payload.settings.notifications.enabled ? "включены" : "выключены"}</p>
    </div>
    <div class="status-item">
      <p class="eyebrow">Коллекции</p>
      <h4>${payload.settings.selectedCollections.join(", ")}</h4>
      <p class="meta-copy">Текущий набор коллекций пользователя.</p>
    </div>
  `;

  elements.galleryStrip.classList.remove("loading");
  elements.galleryStrip.innerHTML = payload.gallery.items.slice(0, 3).map((item) => card(item, true)).join("");
};

const renderGallery = () => {
  const payload = state.payload;
  if (!payload) return;

  const tags = payload.tags.items;
  elements.tagFilters.innerHTML = [
    `<button class="chip ${state.activeTag ? "" : "active"}" data-tag="">Все</button>`,
    ...tags.map((tag) => `<button class="chip ${state.activeTag === tag.id ? "active" : ""}" data-tag="${tag.id}">${tag.name}</button>`),
  ].join("");

  const query = state.query.trim().toLowerCase();
  const filtered = payload.gallery.items.filter((item) => {
    const text = `${item.title} ${item.artist.name} ${(item.tags || []).map((tag) => tag.name).join(" ")}`.toLowerCase();
    const tagPass = !state.activeTag || (item.tags || []).some((tag) => tag.id === state.activeTag);
    return tagPass && (!query || text.includes(query));
  });

  elements.galleryGrid.classList.remove("loading");
  elements.galleryGrid.innerHTML = filtered.length
    ? filtered.map((item) => card(item)).join("")
    : `<p class="empty">По текущему фильтру ничего не найдено.</p>`;
};

const renderFavorites = () => {
  const items = state.payload?.favorites.items || [];
  elements.favoritesGrid.classList.remove("loading");
  elements.favoritesGrid.innerHTML = items.length
    ? items.map((item) => card(item)).join("")
    : `<p class="empty">Избранное пока пустое.</p>`;
};

const renderSettings = () => {
  const settings = state.payload?.settings;
  if (!settings) return;
  elements.settingsForm.classList.remove("loading");
  elements.settingsForm.innerHTML = `
    <div class="settings-grid">
      <div class="setting-item">
        <p class="eyebrow">Коллекции</p>
        <div class="settings-line">
          <div>
            <h4>Выбранные наборы</h4>
            <p class="settings-copy">${settings.selectedCollections.join(", ")}</p>
          </div>
          <div class="settings-actions">
            <button class="chip" data-collections="russian">Только Russian</button>
            <button class="chip" data-collections="world">Только World</button>
            <button class="chip active" data-collections="russian,world">Обе</button>
          </div>
        </div>
      </div>
      <div class="setting-item">
        <p class="eyebrow">Напоминания</p>
        <div class="settings-line">
          <div>
            <h4>Состояние</h4>
            <p class="settings-copy">${settings.notifications.enabled ? "Включены" : "Выключены"} • ${settings.notifications.time}</p>
          </div>
          <div class="settings-actions">
            <button class="chip" data-notifications="${!settings.notifications.enabled}">${settings.notifications.enabled ? "Выключить" : "Включить"}</button>
            <button class="chip" data-time="${settings.notifications.time === "20:00" ? "09:00" : "20:00"}">Сменить время</button>
          </div>
        </div>
      </div>
    </div>
  `;
};

const renderDetail = () => {
  const art = state.detail || state.payload?.main.todayArtwork;
  if (!art) return;
  elements.detailCard.classList.remove("loading");
  elements.detailCard.innerHTML = `
    <div class="detail-visual">${image(art.imageUrl, art.title)}</div>
    <div class="card-actions">
      <div>
        <h3>${art.title}</h3>
        <p class="meta-copy">${art.artist.name} • ${art.year}</p>
      </div>
      <button class="favorite-button ${art.isFavorite ? "active" : ""}" data-favorite="${art.id}">
        ${art.isFavorite ? "♥" : "♡"}
      </button>
    </div>
    <p class="detail-copy">${art.description || "Описание будет загружено из backend."}</p>
    <div class="tag-row">${(art.tags || []).map((tag) => `<span class="tag-pill">${tag.name}</span>`).join("")}</div>
    <ul class="fact-list">${(art.facts || []).map((fact) => `<li>${fact}</li>`).join("")}</ul>
  `;
};

const card = (item, compact = false) => `
  <article class="art-card ${compact ? "compact" : ""}">
    <button class="card-visual" data-detail="${item.id}" type="button">
      ${image(item.imageUrl, item.title)}
    </button>
    <div class="card-actions card-info">
      <div class="card-text">
        <h4 class="card-title">${item.title}</h4>
        <p class="card-artist">${item.artist.name}</p>
        <p class="card-year">${item.year}</p>
      </div>
      <button class="favorite-button ${item.isFavorite ? "active" : ""}" data-favorite="${item.id}">
        ${item.isFavorite ? "♥" : "♡"}
      </button>
    </div>
  </article>
`;

const refresh = async () => {
  const [main, gallery, favorites, settings, tags, status] = await Promise.all([
    request("/main"),
    request("/artworks/gallery"),
    request("/favorites"),
    request("/settings"),
    request("/tags"),
    request("/notifications/today-status"),
  ]);

  state.payload = { main, gallery, favorites, settings, tags, status };
  state.detail = main.todayArtwork;
  render();
};

const updateSettings = async (patch) => {
  await request("/settings", {
    method: "PATCH",
    body: JSON.stringify(patch),
  });
  await refresh();
};

const toggleFavorite = async (artworkId) => {
  const all = [
    ...(state.payload?.gallery.items || []),
    ...(state.payload?.favorites.items || []),
    state.payload?.main.todayArtwork,
    state.detail,
  ].filter(Boolean);
  const current = all.find((item) => item.id === artworkId);
  if (!current) return;

  if (current.isFavorite) {
    await request(`/favorites/${artworkId}`, { method: "DELETE" });
  } else {
    await request("/favorites", {
      method: "POST",
      body: JSON.stringify({ artworkId }),
    });
  }
  await refresh();
  state.detail = await request(`/artworks/${artworkId}`);
  renderDetail();
};

document.addEventListener("click", async (event) => {
  const button = event.target.closest("button");
  if (!button) return;

  clearAppError();
  try {

  if (button.dataset.view) {
    state.view = button.dataset.view;
    render();
    return;
  }

  if (button.dataset.tag !== undefined) {
    state.activeTag = button.dataset.tag;
    state.view = "gallery";
    render();
    return;
  }

  if (button.dataset.detail) {
    state.detail = await request(`/artworks/${button.dataset.detail}`);
    renderDetail();
    return;
  }

  if (button.dataset.favorite) {
    await toggleFavorite(button.dataset.favorite);
    return;
  }

  if (button.dataset.collections) {
    await updateSettings({ selectedCollections: button.dataset.collections.split(",") });
    return;
  }

  if (button.dataset.notifications) {
    await updateSettings({
      notifications: {
        enabled: button.dataset.notifications === "true",
      },
    });
    return;
  }

  if (button.dataset.time) {
    await updateSettings({
      notifications: {
        time: button.dataset.time,
      },
    });
  }
  } catch (error) {
    showAppError(error);
  }
});

elements.searchInput.addEventListener("input", (event) => {
  state.query = event.target.value;
  renderGallery();
});

elements.refreshButton.addEventListener("click", () => refresh().catch(showAppError));

if (!sessionStorage.getItem("dailycanvas_access_token")) {
  showAuth();
} else refresh().catch((error) => {
  if (String(error.message).includes("401")) { sessionStorage.clear(); showAuth("Сессия истекла. Войдите снова."); return; }
  elements.heroCard.classList.remove("loading");
  elements.heroCard.innerHTML = `<p class="empty">Не удалось загрузить данные: ${error.message}</p>`;
});
