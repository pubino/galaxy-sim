import threading
import unittest
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from playwright.sync_api import (
    Browser,
    BrowserContext,
    Page,
    Playwright,
    expect,
    sync_playwright,
)

BUILD = Path(__file__).resolve().parents[1] / "build"


class BrowserTest(unittest.TestCase):
    server: ThreadingHTTPServer
    thread: threading.Thread
    playwright: Playwright
    browser: Browser
    context: BrowserContext
    page: Page

    @classmethod
    def setUpClass(cls) -> None:
        if not (BUILD / "site/index.html").is_file():
            raise RuntimeError("Build the site before running browser tests.")
        # Serve from a subdirectory to match a GitHub project Pages URL.
        cls.server = ThreadingHTTPServer(
            ("127.0.0.1", 0), partial(SimpleHTTPRequestHandler, directory=BUILD)
        )
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.addClassCleanup(cls.server.server_close)
        cls.addClassCleanup(cls.thread.join, 5)
        cls.addClassCleanup(cls.server.shutdown)
        cls.playwright = sync_playwright().start()
        cls.addClassCleanup(cls.playwright.stop)
        cls.browser = cls.playwright.chromium.launch()
        cls.addClassCleanup(cls.browser.close)

    def setUp(self) -> None:
        self.context = self.browser.new_context()
        self.addCleanup(self.context.close)
        self.page = self.context.new_page()
        self.base_url = f"http://127.0.0.1:{self.server.server_port}/site/"

    def test_planar_integrators_and_configurable_presets(self) -> None:
        self.page.goto(self.base_url)
        step = self.page.get_by_role("button", name="Step", exact=True)
        expect(step).to_be_enabled(timeout=45_000)
        chart = self.page.locator("#plot")
        instance = self.page.get_by_label("Instance")
        expect(instance.locator("option")).to_have_count(42)
        expect(instance).to_have_value("FigureEight3")
        expect(self.page.get_by_label("Bodies", exact=True)).to_be_disabled()

        before = chart.evaluate("(canvas) => canvas.toDataURL()")
        step.click()
        expect(self.page.locator("#step-number")).to_have_text("1")
        self.assertNotEqual(chart.evaluate("(canvas) => canvas.toDataURL()"), before)

        dt = self.page.get_by_label("Time step")
        dt.fill("0.002")
        dt.press("Tab")
        step.click()
        expect(self.page.locator("#step-number")).to_have_text("2")
        expect(self.page.locator("#time")).to_have_text("0.00048")

        method = self.page.get_by_label("Integrator")
        method.select_option("animate")
        expect(step).to_be_enabled()
        before = chart.evaluate("(canvas) => canvas.toDataURL()")
        step.click()
        expect(self.page.locator("#step-number")).to_have_text("1")
        self.assertEqual(chart.evaluate("(canvas) => canvas.toDataURL()"), before)
        method.select_option("rk4")
        expect(step).to_be_enabled()
        step.click()
        expect(self.page.locator("#step-number")).to_have_text("1")
        self.assertNotEqual(chart.evaluate("(canvas) => canvas.toDataURL()"), before)

        instance.select_option("Saturn2")
        expect(step).to_be_enabled()
        expect(self.page.locator("#body-count")).to_have_text("100")
        count = self.page.get_by_label("Bodies", exact=True)
        count.fill("24")
        count.press("Tab")
        expect(self.page.locator("#body-count")).to_have_text("24")
        self.page.set_viewport_size({"width": 390, "height": 844})
        self.assertTrue(
            self.page.evaluate("document.documentElement.scrollWidth <= innerWidth")
        )

    def test_runtime_failure_shows_retry(self) -> None:
        self.page.route("**/vendor/pyodide.js", lambda route: route.abort())
        self.page.goto(self.base_url)
        expect(self.page.get_by_role("alert")).to_contain_text("Python could not load")
        expect(self.page.get_by_role("button", name="Reload")).to_be_visible()
