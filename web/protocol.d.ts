export type Vector = number[];
export type Frame = [step: number, time: number, rotation: number, energy: number | undefined | null, positions: Vector[]];
export type Reference = [radii: number[], colors: number[], paths: Vector[][]];
export interface PresetMetadata { name: string; dt: number; warp: number; variable: boolean; count: number }
export interface PresetData { name: string; dt: number; warp: number; variable: boolean; bodies: number[][]; paths: Vector[][] }
export type Timing = { dt: number; rotation: number };
export type Parameters = Timing & { preset: string; count: number; method: string };
export type Request =
  | { type: "reset"; id: number; parameters: Parameters }
  | ({ type: "advance"; id: number; steps: number } & Timing);
export type Reply =
  | { type: "ready"; presets: PresetMetadata[] }
  | { type: "fatal" }
  | { type: "reset"; id: number; frame: Frame; reference: Reference }
  | { type: "advance"; id: number; frame: Frame }
  | { type: "failed"; id: number; message: string };
export interface Proxy<T> {
  toJs(options: { create_pyproxies: false }): T;
  destroy(): void;
}
export interface SimulationProxy {
  set_timing(dt: number, rotation: number): void;
  advance(steps: number): void;
  frame(): Proxy<Frame>;
  reference(): Proxy<Reference>;
  destroy(): void;
}
export interface WorkerScope {
  importScripts(url: string): void;
  location: Location;
  loadPyodide(options: { indexURL: string }): Promise<{
    FS: { writeFile(path: string, contents: string): void };
    loadPackage(name: "numpy"): Promise<void>;
    runPython(code: string): (parameters: string, data: string) => SimulationProxy;
  }>;
  onmessage: (event: MessageEvent<Request>) => void;
  postMessage(message: Reply): void;
}
