const scope = /** @type {import("./protocol").WorkerScope} */ (/** @type {unknown} */ (self));

/** @param {string} file */
async function read(file) {
  const response = await fetch(new URL(file, scope.location.href));
  if (!response.ok) throw new Error(`Could not load ${file}.`);
  return response.text();
}

async function initialize() {
  const vendor = new URL("./vendor/", scope.location.href).href;
  scope.importScripts(new URL("pyodide.js", vendor).href);
  const [python, model, raw] = await Promise.all([
    scope.loadPyodide({ indexURL: vendor }), read("simulation.py"), read("presets.json"),
  ]);
  await python.loadPackage("numpy");
  python.FS.writeFile("/home/pyodide/simulation.py", model);
  /** @type {import("./protocol").PresetData[]} */
  const presets = JSON.parse(raw);
  return { create: python.runPython("from simulation import create\ncreate"), presets };
}

/** @template T @param {import("./protocol").Proxy<T>} proxy */
function convert(proxy) {
  try {
    return proxy.toJs({ create_pyproxies: false });
  } finally {
    proxy.destroy();
  }
}

initialize().then(({ create, presets }) => {
  const catalog = new Map(presets.map((preset) => [preset.name, JSON.stringify(preset)]));
  /** @type {import("./protocol").SimulationProxy | undefined} */
  let simulation;
  scope.onmessage = ({ data }) => {
    try {
      if (data.type === "reset") {
        const preset = catalog.get(data.parameters.preset);
        if (preset === undefined) throw new Error("Unknown instance.");
        const next = create(JSON.stringify(data.parameters), preset);
        simulation?.destroy();
        simulation = next;
        scope.postMessage({
          type: "reset", id: data.id,
          frame: convert(simulation.frame()), reference: convert(simulation.reference()),
        });
        return;
      }
      if (!simulation) throw new Error("Select an instance.");
      simulation.set_timing(data.dt, data.rotation);
      simulation.advance(data.steps);
      scope.postMessage({ type: "advance", id: data.id, frame: convert(simulation.frame()) });
    } catch (error) {
      const message = String(error).match(/ValueError: ([^\r\n]+)/)?.[1];
      scope.postMessage({ type: "failed", id: data.id, message: message ?? "The simulation stopped unexpectedly." });
    }
  };
  scope.postMessage({
    type: "ready",
    presets: presets.map(({ name, dt, warp, variable, bodies }) => (
      { name, dt, warp, variable, count: bodies.length }
    )),
  });
}, () => scope.postMessage({ type: "fatal" }));
