/**
 * TripPack front-end. Plain JavaScript, no framework.
 * All data comes from the TripPack back-end (/api/...). The browser never
 * calls the weather API directly.
 * User text is always inserted with textContent, never as HTML.
 */

const CATEGORY_LABELS = {
  DOCUMENTS: "Documents", CLOTHES: "Clothes", WEATHER: "Weather", ACTIVITY: "Activity",
  TOILETRIES: "Toiletries", TECH: "Tech", OTHER: "Other",
};
const TYPE_LABELS = { CITY: "City trip", BEACH: "Beach holiday", HIKING: "Hiking", BUSINESS: "Business" };

/** App state. */
const state = { trips: [], selectedId: null, items: [], editingId: null, place: null, loadToken: 0 };

const $ = (id) => document.getElementById(id);

// ------------------------------------------------------------------ API calls

/**
 * Calls the back-end and returns the JSON body.
 * Throws an Error with the server's message if the request fails.
 */
async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const problem = await res.json();
      message = problem.detail || problem.title || message;
    } catch { /* body was not JSON */ }
    throw new Error(message);
  }
  return res.status === 204 ? null : res.json();
}

// ------------------------------------------------------------------ helpers

/** Creates an element with optional class and text. */
function el(tag, className, text) {
  const e = document.createElement(tag);
  if (className) e.className = className;
  if (text !== undefined) e.textContent = text;
  return e;
}

/** "Fri 10 Oct" style date for a yyyy-mm-dd string. */
function niceDate(iso) {
  return new Date(iso + "T12:00:00").toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short" });
}

function todayIso() {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 10);
}

/** Shows a short message at the bottom of the screen. */
function toast(message) {
  const t = $("toast");
  t.textContent = message;
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.hidden = true), 3000);
}

/** Disables a button while an async action runs, so it cannot be clicked twice. */
async function busy(button, action) {
  button.disabled = true;
  try { await action(); }
  catch (e) { toast(e.message); }
  finally { button.disabled = false; }
}

// ------------------------------------------------------------------ trip list

async function loadTrips() {
  state.trips = await api("/api/trips");
  renderTripList();
}

function renderTripList() {
  const list = $("trip-list");
  list.replaceChildren();
  $("no-trips").hidden = state.trips.length > 0;
  for (const trip of state.trips) {
    const li = el("li");
    const btn = el("button");
    btn.append(el("strong", null, trip.destination));
    btn.append(el("small", null, `${niceDate(trip.startDate)} to ${niceDate(trip.endDate)}, ${TYPE_LABELS[trip.type]}`));
    btn.setAttribute("aria-current", String(trip.id === state.selectedId));
    btn.addEventListener("click", () => selectTrip(trip.id));
    li.append(btn);
    list.append(li);
  }
}

// ------------------------------------------------------------------ trip detail

/** Opens a trip: shows its data, loads weather and packing list from the back-end. */
async function selectTrip(id) {
  state.selectedId = id;
  renderTripList();
  const trip = state.trips.find((t) => t.id === id);
  $("empty-state").hidden = true;
  $("trip-view").hidden = false;
  $("trip-title").textContent = trip.country ? `${trip.destination}, ${trip.country}` : trip.destination;
  const nights = trip.nights === 1 ? "1 night" : `${trip.nights} nights`;
  $("trip-meta").textContent = `${niceDate(trip.startDate)} to ${niceDate(trip.endDate)}, ${nights}, ${TYPE_LABELS[trip.type]}`;

  $("weather").replaceChildren(el("p", "muted", "Loading weather..."));
  $("weather-source").textContent = "";
  // Block list actions until weather and items are loaded. Otherwise a fast
  // click on "Generate" races with this load and the older response wins.
  const token = ++state.loadToken;
  setListActions(false);
  try {
    const [weather, items] = await Promise.all([api(`/api/trips/${id}/weather`), api(`/api/trips/${id}/items`)]);
    if (token !== state.loadToken) return; // the user opened another trip meanwhile
    renderWeather(weather);
    state.items = items;
    renderItems();
  } catch (e) {
    toast(e.message);
  } finally {
    if (token === state.loadToken) setListActions(true);
  }
}

/** Enables or disables the buttons that change the packing list. */
function setListActions(enabled) {
  $("generate").disabled = !enabled;
  $("add-item").querySelector("button").disabled = !enabled;
}

/** Weather strip: one card per day. */
function renderWeather(weather) {
  const labels = { FORECAST: "Forecast", TYPICAL: "Typical weather, based on last year", UNAVAILABLE: "Not available" };
  $("weather-source").textContent = labels[weather.source] || "";
  const box = $("weather");
  box.replaceChildren();
  if (!weather.days.length) {
    box.append(el("p", "muted", "Weather could not be loaded. The list will still include the basics."));
    return;
  }
  for (const d of weather.days) {
    const card = el("div", "day");
    const icon = d.snowCm > 0 ? "❄️" : d.rainLikely ? "🌧️" : d.maxTemp > 25 ? "☀️" : "⛅";
    card.append(el("span", "icon", icon));
    card.append(el("div", null, niceDate(d.date)));
    card.append(el("div", "temp", `${Math.round(d.minTemp)}° / ${Math.round(d.maxTemp)}°`));
    if (d.maxWindKmh > 40) card.append(el("div", "muted small", `Wind ${Math.round(d.maxWindKmh)} km/h`));
    box.append(card);
  }
}

/** Packing list grouped by category, with progress bar. */
function renderItems() {
  const box = $("items");
  box.replaceChildren();
  const packed = state.items.filter((i) => i.packed).length;
  const total = state.items.length;
  $("progress-bar").style.width = total ? `${(packed / total) * 100}%` : "0";
  $("progress-text").textContent = total ? `${packed} of ${total} items packed` : "No list yet. Click \"Generate list\".";
  $("generate").textContent = total ? "Regenerate list" : "Generate list";

  for (const category of Object.keys(CATEGORY_LABELS)) {
    const inGroup = state.items.filter((i) => i.category === category);
    if (!inGroup.length) continue;
    const group = el("div", "group");
    group.append(el("h4", null, CATEGORY_LABELS[category]));
    inGroup.forEach((item) => group.append(itemRow(item)));
    box.append(group);
  }
}

/** One line of the list: checkbox, name, reason, quantity buttons and remove for own items. */
function itemRow(item) {
  const row = el("div", "item" + (item.packed ? " packed" : ""));

  const check = el("input");
  check.type = "checkbox";
  check.checked = item.packed;
  check.setAttribute("aria-label", `Packed: ${item.name}`);
  check.addEventListener("change", () => updateItem(item, { packed: check.checked }));

  const text = el("div");
  text.append(el("span", "name", item.name));
  text.append(el("span", "reason", item.reason));

  const qty = el("div", "qty");
  const minus = el("button", "btn", "−");
  const plus = el("button", "btn", "+");
  minus.setAttribute("aria-label", `Less ${item.name}`);
  plus.setAttribute("aria-label", `More ${item.name}`);
  minus.disabled = item.quantity <= 1;
  minus.addEventListener("click", () => updateItem(item, { quantity: item.quantity - 1 }));
  plus.addEventListener("click", () => updateItem(item, { quantity: item.quantity + 1 }));
  qty.append(minus, el("span", null, String(item.quantity)), plus);

  row.append(check, text, qty);
  if (item.custom) {
    const remove = el("button", "remove", "✕");
    remove.setAttribute("aria-label", `Remove ${item.name}`);
    remove.addEventListener("click", () => deleteItem(item));
    row.append(remove);
  }
  return row;
}

async function updateItem(item, change) {
  try {
    const updated = await api(`/api/trips/${state.selectedId}/items/${item.id}`, { method: "PATCH", body: change });
    state.items = state.items.map((i) => (i.id === updated.id ? updated : i));
    renderItems();
  } catch (e) {
    toast(e.message);
  }
}

async function deleteItem(item) {
  try {
    await api(`/api/trips/${state.selectedId}/items/${item.id}`, { method: "DELETE" });
    state.items = state.items.filter((i) => i.id !== item.id);
    renderItems();
  } catch (e) {
    toast(e.message);
  }
}

// ------------------------------------------------------------------ trip form

/** Opens the form, empty for a new trip or filled for editing. */
function openTripForm(trip) {
  state.editingId = trip ? trip.id : null;
  state.place = trip ? { name: trip.destination, country: trip.country, latitude: trip.latitude, longitude: trip.longitude } : null;
  $("dialog-title").textContent = trip ? "Edit trip" : "New trip";
  $("destination").value = trip ? trip.destination : "";
  $("picked-place").textContent = trip ? `${trip.destination}, ${trip.country || ""}` : "";
  $("start").value = trip ? trip.startDate : "";
  $("end").value = trip ? trip.endDate : "";
  $("start").min = todayIso();
  $("end").min = todayIso();
  $("type").value = trip ? trip.type : "CITY";
  $("form-error").textContent = "";
  $("suggestions").hidden = true;
  $("trip-dialog").showModal();
  $("destination").focus();
}

/** Destination field with suggestions from GET /api/places, debounced. */
function setupAutocomplete() {
  const input = $("destination");
  const list = $("suggestions");
  let timer;
  input.addEventListener("input", () => {
    state.place = null;
    $("picked-place").textContent = "";
    clearTimeout(timer);
    const q = input.value.trim();
    if (q.length < 2) { list.hidden = true; return; }
    timer = setTimeout(async () => {
      try {
        const places = await api(`/api/places?q=${encodeURIComponent(q)}`);
        list.replaceChildren();
        for (const p of places) {
          const li = el("li", null, [p.name, p.region, p.country].filter(Boolean).join(", "));
          li.addEventListener("click", () => {
            state.place = p;
            input.value = p.name;
            $("picked-place").textContent = `Selected: ${[p.name, p.region, p.country].filter(Boolean).join(", ")}`;
            list.hidden = true;
          });
          list.append(li);
        }
        list.hidden = places.length === 0;
      } catch (e) {
        $("form-error").textContent = e.message;
      }
    }, 300);
  });
}

/** Validates the form in the browser, then saves via POST or PUT. */
async function saveTrip(event) {
  event.preventDefault();
  const error = $("form-error");
  if (!state.place) { error.textContent = "Please pick a destination from the suggestions."; return; }
  if (!$("start").value || !$("end").value) { error.textContent = "Please choose both dates."; return; }
  if ($("end").value < $("start").value) { error.textContent = "The end date must be on or after the start date."; return; }

  const body = {
    destination: state.place.name, country: state.place.country,
    latitude: state.place.latitude, longitude: state.place.longitude,
    startDate: $("start").value, endDate: $("end").value, type: $("type").value,
  };
  try {
    const saved = state.editingId
      ? await api(`/api/trips/${state.editingId}`, { method: "PUT", body })
      : await api("/api/trips", { method: "POST", body });
    $("trip-dialog").close();
    await loadTrips();
    await selectTrip(saved.id);
    toast("Trip saved");
  } catch (e) {
    error.textContent = e.message;
  }
}

// ------------------------------------------------------------------ start

document.addEventListener("DOMContentLoaded", async () => {
  setupAutocomplete();
  $("new-trip").addEventListener("click", () => openTripForm(null));
  $("cancel").addEventListener("click", () => $("trip-dialog").close());
  $("trip-form").addEventListener("submit", saveTrip);
  $("edit-trip").addEventListener("click", () => openTripForm(state.trips.find((t) => t.id === state.selectedId)));

  $("delete-trip").addEventListener("click", () => {
    if (!confirm("Delete this trip and its packing list?")) return;
    deleteTrip();
  });
  const deleteTrip = () => busy($("delete-trip"), async () => {
    await api(`/api/trips/${state.selectedId}`, { method: "DELETE" });
    state.selectedId = null;
    $("trip-view").hidden = true;
    $("empty-state").hidden = false;
    await loadTrips();
    toast("Trip deleted");
  });

  $("generate").addEventListener("click", () => busy($("generate"), async () => {
    state.items = await api(`/api/trips/${state.selectedId}/list/generate`, { method: "POST" });
    renderItems();
  }));

  $("add-item").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = event.target;
    busy(form.querySelector("button"), async () => {
      const item = await api(`/api/trips/${state.selectedId}/items`, {
        method: "POST",
        body: {
          name: form.elements["name"].value,
          category: form.elements["category"].value,
          quantity: Number(form.elements["quantity"].value),
        },
      });
      state.items.push(item);
      renderItems();
      form.reset();
    });
  });

  try {
    await loadTrips();
    if (state.trips.length) await selectTrip(state.trips[0].id);
  } catch (e) {
    toast(e.message);
  }
});
