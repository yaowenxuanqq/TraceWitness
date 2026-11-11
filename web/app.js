function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (ch) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  }[ch]));
}

function render(overview) {
  document.getElementById("banner").textContent = overview.banner || "MOCK_MODE";
  const run = overview.agentRun;
  document.getElementById("task").textContent = run ? run.task : "调查尚未生成";
  document.getElementById("conclusion").textContent = run ? run.conclusion : "";
  document.getElementById("sources").textContent = run && run.sourceIds ? run.sourceIds.join("  ") : "";
  document.getElementById("events").innerHTML = (overview.events || []).map((event) => `
    <tr>
      <td>${esc(event.occurredAt)}</td>
      <td>${esc(event.eventType)}</td>
      <td>${esc(event.summary)}</td>
      <td class="mono">${esc(event.id)}</td>
    </tr>
  `).join("");
  document.getElementById("tools").innerHTML = (overview.toolCalls || []).map((call) => `
    <tr>
      <td>${esc(call.toolName)}</td>
      <td class="mono">${esc((call.sourceIds || []).join("  "))}</td>
      <td class="mono">${esc(call.id)}</td>
    </tr>
  `).join("");
  const places = []
    .concat(overview.sites || [])
    .map((site) => `${site.name} (${site.xM}, ${site.yM}) ${site.id}`);
  (overview.devices || []).forEach((device) => places.push(`${device.name} · ${device.kind} · ${device.id}`));
  document.getElementById("places").innerHTML = places.map((line) => `<p class="mono">${esc(line)}</p>`).join("");
}

fetch("/api/overview")
  .then((response) => {
    if (!response.ok) {
      throw new Error("overview " + response.status);
    }
    return response.json();
  })
  .then(render)
  .catch((error) => {
    document.getElementById("conclusion").textContent = error.message;
  });
