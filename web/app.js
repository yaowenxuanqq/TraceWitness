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

function renderMedia(media) {
  const channel = (media.channels || [])[0];
  document.getElementById("media-note").textContent = channel
    ? `常态 ${media.lowFps} fps，事件时刻 ${media.highFps} fps，音频${media.audioContinuous ? "连续" : "存在间断"}`
    : "还没有媒体时间线";
  if (channel) {
    document.getElementById("channel-audio").src = channel.audioUrl;
  }
  const clips = media.clips || [];
  document.getElementById("clips").innerHTML = clips.map((clip, index) =>
    `<button type="button" class="clip-link" data-index="${index}">事件 ${esc(clip.eventId)} · tick ${esc(clip.startTick)}-${esc(clip.endTick)}</button>`
  ).join("");
  document.getElementById("clips").querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", () => playClip(clips[Number(button.dataset.index)], media.tickMs));
  });
  if (clips[0]) {
    playClip(clips[0], media.tickMs);
  }
  const upload = (media.uploads || [])[media.uploads.length - 1];
  if (upload) {
    showUpload(upload);
  }
}

let clipFrameLoop = 0;

function playClip(clip, tickMs) {
  const audio = document.getElementById("clip-audio");
  const frame = document.getElementById("clip-frame");
  cancelAnimationFrame(clipFrameLoop);
  audio.src = clip.audioUrl;
  const frames = clip.frames || [];
  const show = () => {
    if (!frames.length) {
      return;
    }
    let cursor = 0;
    const ms = audio.currentTime * 1000;
    let chosen = frames[frames.length - 1];
    for (const item of frames) {
      const duration = tickMs / item.fps;
      if (ms < cursor + duration) {
        chosen = item;
        break;
      }
      cursor += duration;
    }
    if (frame.getAttribute("src") !== chosen.url) {
      frame.src = chosen.url;
    }
  };
  const loop = () => {
    show();
    if (!audio.paused && !audio.ended) {
      clipFrameLoop = requestAnimationFrame(loop);
    }
  };
  audio.ontimeupdate = show;
  audio.onplay = loop;
  audio.onpause = () => cancelAnimationFrame(clipFrameLoop);
  audio.onended = () => cancelAnimationFrame(clipFrameLoop);
  if (frames[0]) {
    frame.src = frames[0].url;
  }
}

function showUpload(upload) {
  const video = document.getElementById("upload-video");
  const audio = document.getElementById("upload-audio");
  const type = upload.contentType || "";
  if (type.startsWith("video/")) {
    video.src = upload.url;
    video.hidden = false;
    audio.removeAttribute("src");
    audio.hidden = true;
  } else if (type.startsWith("audio/")) {
    audio.src = upload.url;
    audio.hidden = false;
    video.removeAttribute("src");
    video.hidden = true;
  }
}

document.getElementById("upload").addEventListener("submit", (event) => {
  event.preventDefault();
  const input = document.getElementById("upload-file");
  if (!input.files || !input.files[0]) {
    return;
  }
  const body = new FormData();
  body.append("file", input.files[0]);
  fetch("/api/media/uploads", { method: "POST", body })
    .then((response) => {
      if (!response.ok) {
        throw new Error("upload " + response.status);
      }
      return response.json();
    })
    .then(showUpload)
    .catch((error) => {
      document.getElementById("media-note").textContent = error.message;
    });
});

Promise.all([
  fetch("/api/overview").then((response) => {
    if (!response.ok) {
      throw new Error("overview " + response.status);
    }
    return response.json();
  }),
  fetch("/api/media").then((response) => {
    if (!response.ok) {
      throw new Error("media " + response.status);
    }
    return response.json();
  })
]).then(([overview, media]) => {
  render(overview);
  renderMedia(media);
}).catch((error) => {
  document.getElementById("conclusion").textContent = error.message;
});
