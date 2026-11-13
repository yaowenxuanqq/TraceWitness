let writeToken = "";

function authHeaders(json) {
  const headers = {};
  if (writeToken) {
    headers["X-Trace-Token"] = writeToken;
  }
  if (json) {
    headers["Content-Type"] = "application/json";
  }
  return headers;
}

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
  const note = document.getElementById("overview-note");
  const meta = overview.overviewMeta;
  if (note) {
    note.hidden = !(meta && meta.truncated);
    note.textContent = meta && meta.truncated ? "概览已截断，其余记录请通过 /api/pages/{section} 分页查看。" : "";
  }
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
  const tracks = overview.tracks || [];
  document.getElementById("tracks").innerHTML = tracks.length ? tracks.map((track) => {
    const points = track.points || [];
    const first = points[0] || {};
    const last = points[points.length - 1] || {};
    return `<p class="mono">${esc(track.id)} · ${esc(track.pointCount)} 点 · x ${esc(first.x)} → ${esc(last.x)}</p>`;
  }).join("") : `<p class="muted">还没有轨迹</p>`;
  const observations = overview.observations || [];
  const modeLabel = {
    preferred: "优选帧",
    gate_failure: "门控回退",
    track_break: "跟踪中断",
    inference_error: "推理异常"
  };
  document.getElementById("observations").innerHTML = observations.length ? observations.map((item) => {
    const masked = item.masked || {};
    return `<article class="observation mode-${esc(item.mode)}">
      <img src="${esc(item.thumbnailUrl)}" alt="${esc(modeLabel[item.mode] || item.mode)}">
      <p><span class="badge">${esc(modeLabel[item.mode] || item.mode)}</span> ${esc(item.frameKind)} · 帧 ${esc(item.chosenFrame)} · 属性调用 ${esc(item.attributeCalls)}</p>
      <p class="mono">${esc(masked.upper_color)} · ${esc(masked.posture)} · ${esc(masked.face_region)} · ${esc(item.id)}</p>
    </article>`;
  }).join("") : `<p class="muted">还没有结构化结果</p>`;
  const routeLabel = { local: "本地", cloud: "上送云端", blocked: "不上公网" };
  const fusionLabel = { local: "仅本地", agree: "一致", conflict: "冲突", dual_low: "双低", refused: "拒绝" };
  const conclusionLabel = { conflict: "冲突待复核", insufficient: "证据不足", unknown_business: "未知业务不上公网" };
  const decisions = overview.decisions || [];
  document.getElementById("decisions").innerHTML = decisions.length ? decisions.map((item) => {
    const conclusion = conclusionLabel[item.conclusion] || item.conclusion;
    return `<article class="decision route-${esc(item.route)}">
      <p><span class="badge">${esc(routeLabel[item.route] || item.route)}</span> ${esc(fusionLabel[item.fusion] || item.fusion)} · ${esc(conclusion)}${item.degraded ? " · 历史不足" : ""}</p>
      <p class="mono">${esc(item.caseKey)} · u ${esc(item.uncertainty)} · θ ${esc(item.thetaUEff)} · q ${esc(item.qInput)} · 云端${item.cloudCalled ? "已调用" : "未调用"} · ${esc(item.id)}</p>
    </article>`;
  }).join("") : `<p class="muted">还没有路由决策</p>`;
  renderProfiles(overview);
  renderRetrieval(overview);
  renderTrails(overview);
}

function renderRetrieval(overview) {
  const queries = overview.queries || [];
  document.getElementById("queries").innerHTML = queries.length ? queries.map((query) => {
    const hits = (query.hits || []).map((hit) =>
      `<p class="mono">${esc(hit.rank)}. ${esc(hit.candidateCode)} · ${esc(hit.explanation)}</p>`
    ).join("");
    const excluded = (query.exclusions || []).map((item) =>
      `<p class="mono">排除 ${esc(item.candidateCode)} · ${esc(item.explanation)}</p>`
    ).join("");
    return `<article class="ticket">
      <p><span class="badge">${esc(query.caseKey)}</span> ${esc(query.imageCount)} 张图 · ${esc(query.explainOrder)}</p>
      ${hits || `<p class="muted">没有命中</p>`}
      ${excluded}
    </article>`;
  }).join("") : `<p class="muted">还没有检索</p>`;
  const passages = overview.passages || [];
  document.getElementById("passages").innerHTML = passages.length ? passages.map((item) => {
    const actions = item.alerted && item.status === "pending_human"
      ? `<p><button type="button" data-decision="approve" data-id="${esc(item.id)}">确认告警</button><button type="button" data-decision="reject" data-id="${esc(item.id)}">驳回告警</button></p>`
      : "";
    const label = item.alerted ? (item.status === "confirmed" ? "已确认" : item.status === "rejected" ? "已驳回" : "待确认") : "不告警";
    return `<article class="ticket">
      <p><span class="badge">${esc(label)}</span> ${esc(item.detail)} · 连续 ${esc(item.streak)} 帧</p>
      <p class="mono">${esc(item.caseKey)} · 动作 ${esc(item.actionStatus)} · 已执行 ${item.executed ? "是" : "否"}</p>
      ${actions}
    </article>`;
  }).join("") : `<p class="muted">还没有门线结果</p>`;
  document.getElementById("passages").querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", () => {
      fetch("/api/alerts/" + button.dataset.id + "/decision", {
        method: "POST",
        headers: authHeaders(true),
        body: JSON.stringify({ decision: button.dataset.decision, actor: "investigator" })
      }).then((response) => {
        if (!response.ok) {
          throw new Error("告警没有写入");
        }
        window.location.reload();
      }).catch((error) => {
        button.insertAdjacentText("afterend", error.message);
      });
    });
  });
}

function renderTrails(overview) {
  const doorName = { lobby: "大堂", "unit-a": "一单元门", "unit-b": "二单元门", stair: "楼梯间", roof: "屋顶" };
  const reasonLabel = { unreachable: "不可达", travel_conflict: "旅行时间不足", unknown_door: "未知门点", thinned: "抽稀" };
  const trails = overview.trajectories || [];
  document.getElementById("trails").innerHTML = trails.length ? trails.map((trail) => {
    const points = trail.points || [];
    const line = points.filter((point) => point.kept && !point.thinned);
    const dropped = points.filter((point) => !point.kept).map((point) =>
      `${doorName[point.doorCode] || point.doorCode} · ${reasonLabel[point.reason] || point.reason}`
    );
    return `<article class="ticket">
      <p><span class="badge">${esc(trail.caseKey)}</span> ${esc(trail.profileCode)} · 保留 ${esc(trail.keptCount)} · 剔除 ${esc(trail.droppedCount)} · 地图 ${esc(trail.thinCount)}</p>
      <svg class="trail" viewBox="0 0 100 100" role="img" aria-label="${esc(trail.caseKey)}">
        <rect width="100" height="100" fill="#fff8e8"></rect>
        <polyline points="${line.map((point) => `${point.x},${point.y}`).join(" ")}" fill="none" stroke="#e2a84b" stroke-width="1.5"></polyline>
        ${line.map((point) => `<circle cx="${point.x}" cy="${point.y}" r="1.8" fill="#e8f2ec"></circle><text x="${Number(point.x) + 2}" y="${point.y}" fill="#17211d" aria-hidden="true">${esc(doorName[point.doorCode] || point.doorCode)}</text>`).join("")}
      </svg>
      <p class="mono">${dropped.length ? "剔除 " + dropped.map(esc).join("；") : "没有剔除点"}</p>
    </article>`;
  }).join("") : `<p class="muted">还没有时空轨迹</p>`;
  const companions = overview.companions || [];
  document.getElementById("companions").innerHTML = companions.length ? companions.map((item) => `
    <article class="ticket">
      <p><span class="badge">同行</span> ${esc(item.leftCode)} 与 ${esc(item.rightCode)}</p>
      <p class="mono">${esc(doorName[item.doorCode] || item.doorCode)} · 间隔 ${esc(item.gapSeconds)} 秒 · ${esc(item.id)}</p>
    </article>
  `).join("") : `<p class="muted">这个时间窗里没有同行</p>`;
  const packages = overview.packages || [];
  document.getElementById("packages").innerHTML = packages.length ? packages.map((item) => {
    const actions = item.status === "valid"
      ? `<p><button type="button" data-kind="verify" data-id="${esc(item.id)}">校验</button><button type="button" data-kind="tamper" data-id="${esc(item.id)}">校验篡改</button></p>`
      : "";
    return `<article class="ticket">
      <p><span class="badge">${esc(item.status === "valid" ? "可校验" : "证据缺失")}</span> ${esc(item.caseKey)}</p>
      <p class="mono">${esc(item.digest || item.errorCode)} · ${esc(item.id)}</p>
      ${actions}
    </article>`;
  }).join("") : `<p class="muted">还没有证据包</p>`;
  document.getElementById("packages").querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", () => {
      const body = button.dataset.kind === "tamper" ? { manifest: "tampered\n" } : {};
      fetch("/api/evidence/" + button.dataset.id + "/verify", {
        method: "POST",
        headers: authHeaders(true),
        body: JSON.stringify(body)
      }).then((response) => response.json()).then((result) => {
        button.insertAdjacentText("afterend", result.valid ? " 校验通过" : " 校验失败");
      }).catch((error) => {
        button.insertAdjacentText("afterend", error.message);
      });
    });
  });
  const hardCases = overview.hardCases || [];
  document.getElementById("hard-cases").innerHTML = hardCases.length ? hardCases.map((item) => {
    const actions = item.status === "open"
      ? `<p><button type="button" data-kind="label" data-id="${esc(item.id)}">标为真负例</button></p>`
      : `<p><button type="button" data-kind="export">导出难例</button></p>`;
    const label = item.label ? ` · ${item.label}` : "";
    return `<article class="ticket">
      <p><span class="badge">${esc(item.status === "labeled" ? "已标注" : "待标注")}</span> ${esc(reasonLabel[item.reason] || item.reason)}${esc(label)}</p>
      <p class="mono">${esc(item.caseKey)} · ${esc(item.id)}</p>
      ${actions}
    </article>`;
  }).join("") : `<p class="muted">还没有难例</p>`;
  document.getElementById("hard-cases").querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", () => {
      if (button.dataset.kind === "export") {
        fetch("/api/hard-cases/export").then((response) => response.json()).then((rows) => {
          button.insertAdjacentText("afterend", " 导出 " + rows.length + " 条");
        });
        return;
      }
      fetch("/api/hard-cases/" + button.dataset.id + "/label", {
        method: "POST",
        headers: authHeaders(true),
        body: JSON.stringify({ label: "true_negative", actor: "investigator" })
      }).then((response) => {
        if (!response.ok) {
          throw new Error("标注没有写入");
        }
        window.location.reload();
      }).catch((error) => {
        button.insertAdjacentText("afterend", error.message);
      });
    });
  });
  renderPlugins(overview);
  const compares = overview.compares || [];
  document.getElementById("compares").innerHTML = compares.length ? compares.map((item) => `
    <article class="ticket">
      <p><span class="badge">${item.trained ? "已训练" : "未训练"}</span> ${esc(item.leftName)} ${esc(item.leftScore)} · ${esc(item.rightName)} ${esc(item.rightScore)}</p>
      <p class="mono">优胜 ${esc(item.winner)} · ${esc(item.caseKey)}</p>
    </article>
  `).join("") : "";
}

function renderPlugins(overview) {
  const slotLabel = { active: "在用", previous: "上一版", candidate: "候选", rejected: "未加载", retired: "已换下" };
  const plugins = overview.plugins || [];
  const canRollback = plugins.some((item) => item.slot === "previous");
  document.getElementById("plugins").innerHTML = plugins.length ? plugins.map((item) => {
    const fault = item.slot === "active"
      ? `<p><button type="button" data-kind="fault" data-id="${esc(item.id)}">故障注入</button></p>`
      : "";
    return `<article class="ticket">
      <p><span class="badge">${esc(slotLabel[item.slot] || item.slot)}</span> ${esc(item.name)} ${esc(item.version)} · ${esc(item.reason)} · 健康 ${esc(item.health)}</p>
      <p class="mono">${esc(item.checkOrder)} · 已加载 ${item.loaded ? "是" : "否"} · 核对哈希 ${item.hashChecked ? "是" : "否"} · ${esc(item.resourceMb)}MB</p>
      ${fault}
    </article>`;
  }).join("") + (canRollback ? `<p><button type="button" data-kind="rollback">回滚到上一版</button></p>` : "") : `<p class="muted">还没有插件</p>`;
  document.getElementById("plugins").querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", () => {
      const request = button.dataset.kind === "rollback"
        ? fetch("/api/plugins/rollback", {
          method: "POST",
          headers: authHeaders(true),
          body: JSON.stringify({ actor: "investigator" })
        })
        : fetch("/api/plugins/" + button.dataset.id + "/fault", { method: "POST", headers: authHeaders(false) });
      request.then((response) => {
        if (!response.ok) {
          throw new Error("插件操作没有写入");
        }
        window.location.reload();
      }).catch((error) => {
        button.insertAdjacentText("afterend", error.message);
      });
    });
  });
  const rollbacks = overview.rollbacks || [];
  document.getElementById("rollbacks").innerHTML = rollbacks.length ? rollbacks.map((item) => `
    <article class="ticket">
      <p><span class="badge">${esc(item.reason)}</span> ${esc(item.actor)}</p>
      <p class="mono">${esc(item.fromId)} → ${esc(item.toId || "无")}</p>
    </article>
  `).join("") : "";
}

function renderProfiles(overview) {
  const galleryLabel = { resident: "居民档", stranger: "陌生人档" };
  const statusLabel = {
    auto: "已自动合并",
    pending_human: "待审批",
    forbidden: "禁止合并",
    approved: "已批准",
    rejected: "已驳回",
    open: "频次工单",
    active: "在档",
    merged: "已并入"
  };
  const doorName = {};
  (overview.doors || []).forEach((door) => {
    doorName[door.code] = door.name;
  });
  const profiles = overview.profiles || [];
  document.getElementById("profiles").innerHTML = profiles.length ? profiles.map((item) => `
    <article class="ticket">
      <p><span class="badge">${esc(galleryLabel[item.gallery] || item.gallery)}</span> ${esc(item.displayName)} · ${esc(statusLabel[item.status] || item.status)}</p>
      <p class="mono">${esc(doorName[item.addressLabel] || item.addressLabel || "无住址")} · 到访 ${esc(item.visitCount)} · 档心 ${esc(item.centroid)} · ${esc(item.id)}</p>
    </article>
  `).join("") : `<p class="muted">还没有档案</p>`;
  const reasonLabel = {
    matched: "旅行时间允许",
    unreachable: "门点不可达",
    travel_conflict: "旅行时间冲突",
    ambiguous: "双库分数过近",
    frequent_door: "同一门点频次达到 3 次",
    created: "新建陌生人档"
  };
  const tickets = overview.tickets || [];
  document.getElementById("tickets").innerHTML = tickets.map((item) => {
    const actions = item.status === "pending_human" && item.ticketKind === "merge"
      ? `<p><button type="button" data-decision="approve" data-id="${esc(item.id)}">批准合并</button><button type="button" data-decision="reject" data-id="${esc(item.id)}">驳回</button></p>`
      : "";
    const timing = item.observedSeconds == null && item.travelSeconds == null
      ? ""
      : ` · 观测 ${esc(item.observedSeconds)}s · 最短 ${esc(item.travelSeconds)}s`;
    return `<article class="ticket status-${esc(item.status)}">
      <p><span class="badge">${esc(statusLabel[item.status] || item.status)}</span> ${esc(reasonLabel[item.reason] || item.reason)} · ${esc(item.detail)}</p>
      <p class="mono">${esc(item.caseKey)}${timing} · ${esc(item.id)}</p>
      ${actions}
    </article>`;
  }).join("");
  document.getElementById("tickets").querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", () => {
      fetch("/api/profiles/tickets/" + button.dataset.id + "/decision", {
        method: "POST",
        headers: authHeaders(true),
        body: JSON.stringify({ decision: button.dataset.decision, actor: "investigator" })
      }).then((response) => {
        if (!response.ok) {
          throw new Error("审批没有写入");
        }
        window.location.reload();
      }).catch((error) => {
        button.insertAdjacentText("afterend", error.message);
      });
    });
  });
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
  fetch("/api/media/uploads", { method: "POST", headers: authHeaders(false), body })
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

fetch("/api/session").then((response) => {
  if (!response.ok) {
    throw new Error("session " + response.status);
  }
  return response.json();
}).then((session) => {
  writeToken = session.token || "";
  return Promise.all([
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
  ]);
}).then(([overview, media]) => {
  render(overview);
  renderMedia(media);
}).catch((error) => {
  document.getElementById("conclusion").textContent = error.message;
});
