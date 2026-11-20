import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from typing import Any, ClassVar

import numpy as np

from web.simulation import create

ROOT = Path(__file__).resolve().parents[1]
METHODS = {
    "hermite": "Hermite",
    "midpoint": "Midpoint (Leapfrog)",
    "euler": "Euler",
    "rk4": "RungeKutta4",
    "trapnewt": "TrapNewt",
    "animate": "Animate Only",
}


class GalaxyTest(unittest.TestCase):
    directory: ClassVar[str]
    presets: ClassVar[dict[str, Any]]

    @classmethod
    def setUpClass(cls) -> None:
        directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(directory.cleanup)
        cls.directory = directory.name
        subprocess.run(
            [
                "javac",
                "--release",
                "25",
                "-d",
                cls.directory,
                *sorted((ROOT / "astro/galaxy").glob("*.java")),
                *sorted((ROOT / "myutil").glob("*.java")),
                ROOT / "scripts/OrbitReference.java",
            ],
            check=True,
            timeout=60,
        )
        cls.presets = {preset["name"]: preset for preset in cls.java("catalog")}

    @classmethod
    def java(cls, *args: str) -> list[Any]:
        output = subprocess.check_output(
            [
                "java",
                "--add-opens",
                "java.base/java.lang=ALL-UNNAMED",
                "-Djava.awt.headless=true",
                "-cp",
                cls.directory,
                "astro.galaxy.OrbitReference",
                *args,
            ],
            text=True,
            timeout=45,
        )
        result = json.loads(output.splitlines()[-1])
        assert isinstance(result, list)
        return result

    def compare(self, name: str, method: str, count: int | None = None) -> None:
        preset = self.presets[name]
        model = create(
            json.dumps(
                {
                    "method": method,
                    "dt": preset["dt"],
                    "rotation": 0.23,
                    "count": len(preset["bodies"]) if count is None else count,
                }
            ),
            json.dumps(preset),
        )
        before = 0
        for step, elapsed, angle, bodies, derivatives in self.java(
            "trace",
            name,
            METHODS[method],
            "" if count is None else str(count),
            "0.23",
            "0",
            "1",
            "16",
            "32",
        ):
            if step > before:
                model.advance(step - before)
            body_data = np.array(bodies)
            actual = np.column_stack((model.positions, model.velocities))
            np.testing.assert_allclose(actual, body_data[:, 3:], rtol=2e-11, atol=1e-11)
            np.testing.assert_allclose(model.masses, body_data[:, 0], rtol=2e-14)
            np.testing.assert_allclose(
                np.column_stack((model.acceleration, model.jerk)),
                derivatives,
                rtol=2e-10,
                atol=2e-10,
            )
            self.assertEqual(model.time, elapsed)
            self.assertEqual(model.angle, angle)
            before = step

    def test_all_exported_presets_follow_java_hermite(self) -> None:
        self.assertEqual(len(self.presets), 42)
        deployed = json.loads((ROOT / "build/site/presets.json").read_text())
        self.assertEqual(deployed, list(self.presets.values()))
        for name in self.presets:
            with self.subTest(preset=name):
                self.compare(name, "hermite")

    def test_all_integrator_branches(self) -> None:
        for method in METHODS:
            with self.subTest(method=method):
                self.compare("FigureEight3", method)

    def test_configurable_body_counts(self) -> None:
        for name, count in (
            ("LagrangeN", 17),
            ("LagrangeNa", 17),
            ("Saturn", 31),
            ("Saturn2", 31),
            ("L1-L5 M/m=2", 9),
            ("L1-L5 M/m=40", 5),
        ):
            with self.subTest(preset=name, count=count):
                self.compare(name, "hermite", count)

    def test_singular_unsoftened_step_preserves_state(self) -> None:
        preset = self.presets["LagrangeNa"]
        model = create(
            '{"count":100,"method":"trapnewt","dt":0.001,"rotation":0}',
            json.dumps(preset),
        )
        before = model.frame()
        self.assertIsNone(before[3])
        with self.assertRaisesRegex(ValueError, "singularity"):
            model.advance(1)
        self.assertEqual(model.frame(), before)
