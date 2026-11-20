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

const preset = find("preset", HTMLSelectElement);
const dt = find("dt", HTMLInputElement);
const warp = find("warp", HTMLInputElement);
const bodies = find("count", HTMLInputElement);
const method = find("method", HTMLSelectElement);
const rotation = find("rotation", HTMLInputElement);
const canvas = find("plot", HTMLCanvasElement);
const zoom = find("zoom", HTMLInputElement);
const showPaths = find("paths", HTMLInputElement);
const showTrails = find("trails", HTMLInputElement);
const iteration = find("step-number", HTMLElement);
const time = find("time", HTMLElement);
const count = find("body-count", HTMLElement);
const energyLabel = find("energy", HTMLElement);
/** @type {Map<string, import("./protocol").PresetMetadata>} */
const presets = new Map();
/** @type {import("./protocol").Reference | null} */
let active = null;
/** @type {number[][][]} */
let trails = [];
let previousStep = -1;
let bounds = 1;

/** @param {import("./protocol").PresetMetadata[]} catalog */
export function loadPresets(catalog) {
  for (const item of catalog) {
    presets.set(item.name, item);
    preset.add(new Option(item.name, item.name));
  }
  preset.value = "FigureEight3";
  selectPreset();
}

export function selectPreset() {
  const item = presets.get(preset.value);
  if (!item) throw new Error("Unknown instance.");
  dt.value = String(item.dt);
  warp.value = String(item.warp);
  bodies.value = String(item.count);
  bodies.disabled = !item.variable;
  rotation.value = "0";
  zoom.value = "0";
}

export function stepsPerFrame() {
  return warp.valueAsNumber;
}

export function getTiming() {
  return { dt: dt.valueAsNumber, rotation: rotation.valueAsNumber };
}

export function getParameters() {
  return { preset: preset.value, ...getTiming(), count: bodies.valueAsNumber, method: method.value };
}

/** @param {number} angle */
function camera(angle) {
  const c = Math.cos(angle);
  const s = Math.sin(angle);
  return (/** @type {number[]} */ [x, y]) => [c * x + s * y, -s * x + c * y, 0];
}

/** @param {number[][]} points */
function extent(points) {
  return points.reduce((max, point) => Math.max(max, Math.hypot(...point)), 0);
}

/** @param {import("./protocol").Frame} frame @param {import("./protocol").Reference | null} reference */
export function render(frame, reference) {
  if (!reference) return;
  const [step, times, angle, energy, positions] = frame;
  const [radii, colors, paths] = reference;
  iteration.textContent = step.toLocaleString();
  time.textContent = times.toFixed(5);
  count.textContent = String(positions.length);
  energyLabel.textContent = typeof energy === "number" ? energy.toPrecision(7) : "—";

  if (active !== reference) {
    active = reference;
    bounds = Math.max(0.02, extent(positions), ...paths.map(extent)) * 1.12;
    trails = positions.map(() => []);
    previousStep = -1;
    showPaths.disabled = !paths.length;
  }
  const fit = extent(positions);
  if (fit > bounds && fit < 4 * bounds) bounds = fit * 1.12;
  if (!showTrails.checked) {
    trails = positions.map(() => []);
  } else if (step !== previousStep) {
    positions.forEach((position, i) => {
      trails[i].push(position);
      if (trails[i].length > 220) trails[i].shift();
    });
  }
  previousStep = step;

  const painter = canvas.getContext("2d");
  if (!painter) throw new Error("Canvas is unavailable.");
  const w = canvas.width;
  const h = canvas.height;
  const scale = Math.min(w, h) / (2 * bounds) * (2 ** zoom.valueAsNumber);
  const project = camera(angle);

  painter.fillStyle = "#f5f8fb";
  painter.fillRect(0, 0, w, h);
  painter.strokeStyle = "#e0e8ef";
  painter.lineWidth = 1;
  painter.beginPath();
  painter.moveTo(w / 2, 0);
  painter.lineTo(w / 2, h);
  painter.moveTo(0, h / 2);
  painter.lineTo(w, h / 2);
  painter.stroke();

  if (showPaths.checked) {
    painter.strokeStyle = "#8094aa";
    painter.lineWidth = 1.6;
    for (const path of paths) {
      painter.beginPath();
      path.forEach((point, i) => {
        const [x, y] = project(point);
        if (i === 0) painter.moveTo(w / 2 + scale * x, h / 2 - scale * y);
        else painter.lineTo(w / 2 + scale * x, h / 2 - scale * y);
      });
      painter.stroke();
    }
  }

  if (showTrails.checked) {
    painter.strokeStyle = "#517693";
    painter.lineWidth = 1.4;
    for (const trail of trails) {
      painter.beginPath();
      trail.forEach((point, i) => {
        const [x, y] = project(point);
        if (i === 0) painter.moveTo(w / 2 + scale * x, h / 2 - scale * y);
        else painter.lineTo(w / 2 + scale * x, h / 2 - scale * y);
      });
      painter.stroke();
    }
  }

  const visible = positions.map((point, index) => ({ index, projection: project(point) }));

  for (const { index, projection } of visible) {
    const size = Math.min(38, Math.max(4.5, radii[index] * scale));
    const [x, y] = projection;
    painter.beginPath();
    painter.arc(w / 2 + scale * x, h / 2 - scale * y, size, 0, 2 * Math.PI);
    painter.fillStyle = `#${colors[index].toString(16).padStart(6, "0")}`;
    painter.fill();
    painter.strokeStyle = "#334f68";
    painter.lineWidth = 1.3;
    painter.stroke();
  }
}
