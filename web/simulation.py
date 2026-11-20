"""The original Galaxy integrators and configurable planar initial conditions."""

import json
from math import cos, isfinite, pi, sin, sqrt, tau
from typing import Any, TypedDict, cast

import numpy as np
from numpy.typing import NDArray

G = 1.177e-4
R0 = 0.01
METHODS = ("hermite", "midpoint", "euler", "rk4", "trapnewt", "animate")
Array = NDArray[np.float64]


class Preset(TypedDict):
    name: str
    variable: bool
    bodies: list[list[float]]
    paths: list[list[list[float]]]


class JavaRandom:
    def __init__(self, seed: int) -> None:
        self.state = (seed ^ 0x5DEECE66D) & ((1 << 48) - 1)

    def bits(self, width: int) -> int:
        self.state = (self.state * 0x5DEECE66D + 11) & ((1 << 48) - 1)
        return self.state >> (48 - width)

    def random(self) -> float:
        return ((self.bits(26) << 27) + self.bits(27)) / (1 << 53)

    def color(self) -> int:
        rgb = 0
        for _ in range(3):
            component = np.float32(0.5 + self.random() / 2)
            rgb = (rgb << 8) | int(component * np.float32(255) + np.float32(0.5))
        return rgb


def ring_body(radius: float, angle: float, speed: float) -> list[float]:
    x, y = cos(angle), sin(angle)
    return [radius * x, radius * y, -speed * y, speed * x]


def conditions(preset: Preset, count: int) -> Array:
    name = preset["name"]
    if count == len(preset["bodies"]):
        return np.array(preset["bodies"], dtype=np.float64)

    rng = JavaRandom(524)
    bodies = [[1, R0, rng.color(), 0, 0, 0, 0] for _ in range(count)]
    values = np.array(bodies, dtype=np.float64)
    if name in ("LagrangeN", "LagrangeNa"):
        radius, fraction = (0.1, 0.05) if name == "LagrangeN" else (0.0, 0.55)
        speed = fraction * sqrt(3 * G * (count - 1) / R0)
        for index in range(count):
            angle = (24.0 * index) / count * 2 * pi / 24
            values[index, 3:] = ring_body(radius, angle, speed)
    elif name.startswith("L1-L5"):
        prefix = min(count, len(preset["bodies"]))
        values[:prefix] = preset["bodies"][:prefix]
    else:
        # Both Saturn presets use the same dust-ring radius and provisional speed.
        values[0] = preset["bodies"][0]
        first = 1
        if name == "Saturn2":
            values[1] = preset["bodies"][1]
            first = 2
        for index in range(first, count):
            radius = 0.05
            speed = sqrt(G * values[0, 0] / radius) / radius * radius
            angle = index / (count - float(first)) * 2 * pi
            values[index] = [
                0.01 * values[0, 0] / count,
                0.002,
                0xFFFFFF,
                *ring_body(radius, angle, speed),
            ]

    for axis in (5, 6):
        momentum = 0.0
        for index in range(1, count):
            momentum += float(values[index, 0] * values[index, axis])
        values[0, axis] = -momentum / values[0, 0]
    return values


def pair_delta(position: Array) -> tuple[Array, Array]:
    delta = position[None, :, :] - position[:, None, :]
    x, y = delta[..., 0], delta[..., 1]
    return delta, x * x + y * y


def ordered_sum(terms: Array, initial: Array | None = None) -> Array:
    if initial is not None:
        terms = np.concatenate((initial[:, None, :], terms), axis=1)
    return np.cumsum(terms, axis=1)[:, -1, :]


class Simulation:
    def __init__(
        self, preset: Preset, count: int, method: str, dt: float, rotation: float
    ) -> None:
        data = conditions(preset, count)
        self.positions: Array = data[:, 3:5].copy()
        self.velocities: Array = data[:, 5:7].copy()
        self.masses: Array = data[:, 0]
        self.radii = cast(list[float], data[:, 1].tolist())
        self.colors = [int(rgb) for rgb in data[:, 2]]
        self.paths = preset["paths"]
        self.dt = dt
        self.rotation = rotation
        self.method = method
        self.steps = 0
        self.time = self.angle = 0.0
        self.acceleration: Array = np.zeros_like(self.positions)
        self.jerk: Array = np.zeros_like(self.positions)
        if method == "midpoint":
            self.acceleration = self.force(self.positions)
            self.velocities += self.acceleration * dt / 2
        elif method == "hermite":
            self.acceleration, self.jerk = self.hermite_force(
                self.positions, self.velocities, initial=True
            )

    def force(self, positions: Array, softened: bool = True) -> Array:
        delta, squared = pair_delta(positions)
        r = np.sqrt(squared)
        cube = r * r * r
        if softened:
            np.maximum(cube, R0 * R0 * R0, out=cube)
        np.fill_diagonal(cube, np.inf)
        return ordered_sum(G * self.masses[None, :, None] * delta / cube[..., None])

    def hermite_force(
        self, positions: Array, velocities: Array, initial: bool = False
    ) -> tuple[Array, Array]:
        delta, squared = pair_delta(positions)
        velocities_delta = velocities[None, :, :] - velocities[:, None, :]
        softened = np.maximum(squared, R0 * R0)
        cube = softened * np.sqrt(softened)
        np.fill_diagonal(cube, np.inf)
        mask = 1.0 if initial else (squared < R0 * R0)
        dot = (
            delta[..., 0] * velocities_delta[..., 0]
            + delta[..., 1] * velocities_delta[..., 1]
        )
        rv = dot / softened
        change = velocities_delta - (3 * mask * rv)[..., None] * delta
        masses = G * self.masses[None, :, None]
        return (
            ordered_sum(masses * delta / cube[..., None]),
            ordered_sum(masses * change / cube[..., None]),
        )

    def hermite_step(
        self, positions: Array, velocities: Array
    ) -> tuple[Array, Array, Array, Array]:
        dt, a0, j0 = self.dt, self.acceleration, self.jerk
        predictor = positions + (
            velocities * dt + a0 * dt * dt / 2 + j0 * dt * dt * dt / 6
        )
        predictor_v = velocities + (a0 * dt + j0 * dt * dt / 2)
        a1, j1 = self.hermite_force(predictor, predictor_v)
        velocity = velocities + (a0 + a1) * dt / 2 + (j0 - j1) * dt * dt / 12
        position = (
            positions + (velocities + velocity) * dt / 2 + (a0 - a1) * dt * dt / 12
        )
        return position, velocity, a1, j1

    def runge_kutta(self, x: Array, v: Array) -> tuple[Array, Array, Array]:
        dt = self.dt
        k1x, k1v = dt * v, dt * self.force(x)
        k2x, k2v = dt * (v + k1v / 2), dt * self.force(x + k1x / 2)
        k3x, k3v = dt * (v + k2v / 2), dt * self.force(x + k2x / 2)
        a = self.force(x + k3x)
        k4x, k4v = dt * (v + k3v), dt * a
        return (
            x + (k1x / 6 + k2x / 3 + k3x / 3 + k4x / 6),
            v + (k1v / 6 + k2v / 3 + k3v / 3 + k4v / 6),
            a,
        )

    def trap_newt(self, x: Array, v: Array) -> tuple[Array, Array, Array]:
        a = self.force(x, softened=False)
        delta, squared = pair_delta(x)
        r: Array = np.sqrt(squared)
        r2 = r * r
        fifth = r * r * r * r * r
        np.fill_diagonal(fifth, np.inf)

        def correction(vector: Array, scale: float) -> Array:
            source, target = vector[None, :, :], vector[:, None, :]
            dot_source = delta[..., 0] * source[..., 0] + delta[..., 1] * source[..., 1]
            dot_target = delta[..., 0] * target[..., 0] + delta[..., 1] * target[..., 1]
            coeff = scale * G * self.masses[None, :, None]
            return (
                coeff
                * (r2[..., None] * source - 3 * delta * dot_source[..., None])
                / fifth[..., None]
                - coeff
                * (r2[..., None] * target - 3 * delta * dot_target[..., None])
                / fifth[..., None]
            )

        dt2 = self.dt * self.dt
        first = ordered_sum(correction(v, dt2 / 2), a * self.dt)
        second = ordered_sum(correction(first, dt2 / 4), first)
        return x + self.dt * (v + second / 2), v + second, a

    def advance(self, steps: int) -> None:
        if type(steps) is not int or not 1 <= steps <= 500:
            raise ValueError("Use 1 to 500 steps per update.")
        try:
            with np.errstate(divide="raise", invalid="raise", over="raise"):
                for _ in range(steps):
                    x, v = self.positions, self.velocities
                    if self.method == "hermite":
                        x, v, self.acceleration, self.jerk = self.hermite_step(x, v)
                    elif self.method == "rk4":
                        x, v, self.acceleration = self.runge_kutta(x, v)
                    elif self.method == "trapnewt":
                        x, v, self.acceleration = self.trap_newt(x, v)
                    elif self.method == "euler":
                        a = self.force(x)
                        x, v, self.acceleration = x + v * self.dt, v + a * self.dt, a
                    elif self.method == "midpoint":
                        x = x + v * self.dt
                        a = self.force(x)
                        v, self.acceleration = v + a * self.dt, a
                    self.positions, self.velocities = x, v
                    self.steps += 1
                    self.time += self.dt
                    self.angle += self.dt * self.rotation
        except FloatingPointError as error:
            raise ValueError(
                "The integrator reached a singularity or exceeded its numerical range."
            ) from error

    def energy(self) -> float | None:
        _, squared = pair_delta(self.positions)
        pairs = np.triu_indices(len(self.masses), k=1)
        with np.errstate(divide="ignore", invalid="ignore", over="ignore"):
            potential = (
                -G
                * self.masses[pairs[0]]
                * self.masses[pairs[1]]
                / np.sqrt(squared[pairs])
            )
            vx, vy = self.velocities.T
            kinetic = self.masses * (vx * vx + vy * vy) / 2
            energy = float(np.sum(kinetic) + np.sum(potential))
        return energy if isfinite(energy) else None

    def frame(self) -> tuple[int, float, float, float | None, list[list[float]]]:
        return (
            self.steps,
            self.time / tau,
            self.angle,
            self.energy(),
            cast(list[list[float]], self.positions.tolist()),
        )

    def reference(self) -> tuple[list[float], list[int], list[list[list[float]]]]:
        return self.radii, self.colors, self.paths

    def set_timing(self, dt: float, rotation: float) -> None:
        step = real(dt, 1e-8, 0.2, "Time step")
        rate = real(rotation, -1000, 1000, "Rotation rate")
        self.dt, self.rotation = step, rate


def real(value: Any, low: float, high: float, name: str) -> float:
    if type(value) not in (float, int) or not low <= value <= high:
        raise ValueError(f"{name} must be a number from {low:g} to {high:g}.")
    return float(value)


def create(parameters: str, exported: str) -> Simulation:
    options = json.loads(parameters)
    if not isinstance(options, dict):
        raise ValueError("Choose an integrator and simulation parameters.")
    preset = cast(Preset, json.loads(exported))
    count, method = options.get("count"), options.get("method")
    if type(count) is not int or not 2 <= count <= 200:
        raise ValueError("Use 2 to 200 bodies.")
    if not preset["variable"] and count != len(preset["bodies"]):
        raise ValueError("This preset has a fixed number of bodies.")
    if not isinstance(method, str) or method not in METHODS:
        raise ValueError("Choose a supported integrator.")
    dt = real(options.get("dt"), 1e-8, 0.2, "Time step")
    rotation = real(options.get("rotation"), -1000, 1000, "Rotation rate")
    return Simulation(preset, count, method, dt, rotation)
