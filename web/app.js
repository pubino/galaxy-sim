import { getParameters, getTiming, loadPresets, selectPreset, render, stepsPerFrame } from "./view.js";

/**
 * @template {Element} T
 * @param {string} id
 * @param {{new(): T}} constructor
 */
function find(id, constructor) {
  const node = document.getElementById(id);
  if (!(node instanceof constructor)) throw new Error(`Missing element ${id}`);
  return node;
}

const form = find("settings", HTMLFormElement);
const inputs = find("inputs", HTMLFieldSetElement);
const play = find("play", HTMLButtonElement);
const step = find("step", HTMLButtonElement);
const reset = find("reset", HTMLButtonElement);
const retry = find("retry", HTMLButtonElement);
const status = find("status", HTMLElement);
const error = find("error", HTMLElement);

const worker = new Worker(new URL("./worker.js", import.meta.url));
/** @type {import("./protocol").Frame | null} */
let frame = null;
/** @type {import("./protocol").Reference | null} */
let reference = null;
let timer = 0;
let running = false;
let pending = true;
let failed = false;
let generation = 0;

function controls() {
  inputs.disabled = running || (pending && !frame);
  play.disabled = !frame || failed;
  step.disabled = play.disabled || running || pending;
  reset.disabled = pending && !frame;
  play.textContent = running ? "Pause" : (frame?.[0] ? "Resume" : "Start");
}

function stop() {
  running = false;
  window.clearTimeout(timer);
  timer = 0;
}

/** @param {number} steps */
function advance(steps) {
  if (pending || failed || !frame) return;
  if (!form.reportValidity()) {
    stop();
    status.textContent = "Correct the highlighted input.";
    controls();
    return;
  }
  pending = true;
  controls();
  worker.postMessage({ type: "advance", id: generation, steps, ...getTiming() });
}

function schedule() {
  if (!running || pending || timer) return;
  timer = window.setTimeout(() => {
    timer = 0;
    advance(stepsPerFrame());
  }, 40);
}

function initialize() {
  stop();
  inputs.disabled = false;
  generation++;
  failed = !form.reportValidity();
  error.hidden = true;
  if (failed) {
    pending = false;
    status.textContent = "Correct the highlighted input.";
  } else {
    frame = null;
    reference = null;
    pending = true;
    status.textContent = "Preparing the simulation…";
    worker.postMessage({ type: "reset", id: generation, parameters: getParameters() });
  }
  controls();
}

function showFatal() {
  stop();
  worker.terminate();
  inputs.disabled = play.disabled = step.disabled = reset.disabled = true;
  status.textContent = "";
  error.textContent = "Python could not load. Reload to try again.";
  error.hidden = retry.hidden = false;
}

worker.onerror = showFatal;
worker.onmessage = (/** @type {MessageEvent<import("./protocol").Reply>} */ { data }) => {
  if (data.type === "ready") {
    loadPresets(data.presets);
    initialize();
  }
  if (data.type === "fatal") showFatal();
  if (!("id" in data) || data.id !== generation) return;
  pending = false;
  if (data.type === "failed") {
    failed = true;
    stop();
    status.textContent = "";
    error.textContent = data.message;
    error.hidden = false;
    controls();
    return;
  }
  if (data.type === "reset") {
    reference = data.reference;
    status.textContent = "Ready to start.";
  }
  frame = data.frame;
  render(frame, reference);
  controls();
  schedule();
};

form.addEventListener("submit", (event) => {
  event.preventDefault();
  if (!inputs.disabled) initialize();
});
form.addEventListener("change", (event) => {
  if (event.target instanceof Element && event.target.matches("[data-initial]")) {
    if (!inputs.disabled) {
      if (event.target.id === "preset") selectPreset();
      initialize();
    }
  } else if (frame) {
    render(frame, reference);
  }
});
form.addEventListener("input", (event) => {
  if (frame && event.target instanceof Element && event.target.matches("[data-view]")) render(frame, reference);
});
play.addEventListener("click", () => {
  if (running) {
    stop();
    status.textContent = "Paused.";
  } else if (form.reportValidity()) {
    running = true;
    status.textContent = "Running…";
    schedule();
  }
  controls();
});
step.addEventListener("click", () => {
  status.textContent = "Paused.";
  advance(1);
});
reset.addEventListener("click", initialize);
retry.addEventListener("click", () => window.location.reload());
