"""End-to-end check of the TripPack UI in a real browser (Playwright).
Creates a trip through the form, generates the list, ticks items, adds an
own item, and saves desktop and phone screenshots to docs/screenshots/.

    python tools/ui_check.py      (the app must be running on localhost:8080)
"""
import sys
from datetime import date, timedelta
from pathlib import Path

from playwright.sync_api import sync_playwright, expect

BASE = "http://localhost:8080"
OUT = Path(__file__).resolve().parent.parent / "docs" / "screenshots"
OUT.mkdir(parents=True, exist_ok=True)


def create_trip(page, city, start, end, trip_type):
    page.click("#new-trip")
    page.fill("#destination", city)
    page.locator("#suggestions li").first.click()
    page.fill("#start", start.isoformat())
    page.fill("#end", end.isoformat())
    page.select_option("#type", trip_type)
    page.click("#trip-form button[type=submit]")
    expect(page.locator("#trip-title")).to_contain_text(city)


with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page(viewport={"width": 1366, "height": 900}, device_scale_factor=1)
    errors = []
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.on("dialog", lambda d: d.accept())
    page.goto(BASE)

    today = date.today()
    create_trip(page, "Doha", today + timedelta(days=3), today + timedelta(days=7), "CITY")
    page.click("#generate")
    expect(page.locator(".item").first).to_be_visible()
    page.locator(".item input[type=checkbox]").nth(0).check()
    page.locator(".item input[type=checkbox]").nth(1).check()
    page.fill("#add-item input[name=name]", "Travel adapter")
    page.select_option("#add-item select", "TECH")
    page.click("#add-item button")
    expect(page.locator(".item", has_text="Travel adapter")).to_be_visible()
    expect(page.locator("#progress-text")).to_contain_text("2 of")
    page.screenshot(path=str(OUT / "01_desktop_doha.png"), full_page=True)

    # a second trip far in the future, uses "typical weather"
    create_trip(page, "Oslo", today + timedelta(days=60), today + timedelta(days=64), "HIKING")
    page.click("#generate")
    expect(page.locator(".item").first).to_be_visible()
    page.screenshot(path=str(OUT / "02_desktop_oslo_typical.png"), full_page=True)

    # form validation in the browser
    page.click("#new-trip")
    page.fill("#destination", "Berlin")
    page.click("#trip-form button[type=submit]")
    expect(page.locator("#form-error")).to_contain_text("pick a destination")
    page.screenshot(path=str(OUT / "03_form_validation.png"))
    page.click("#cancel")

    # phone view
    phone = browser.new_page(viewport={"width": 390, "height": 844}, device_scale_factor=2, is_mobile=True)
    phone.goto(BASE)
    expect(phone.locator(".item").first).to_be_visible()
    phone.screenshot(path=str(OUT / "04_phone_list.png"), full_page=True)
    tablet = browser.new_page(viewport={"width": 820, "height": 1180})
    tablet.goto(BASE)
    expect(tablet.locator(".item").first).to_be_visible()
    tablet.screenshot(path=str(OUT / "05_tablet.png"))

    browser.close()
    if errors:
        print("JS errors:", errors)
        sys.exit(1)
    print("UI check passed, screenshots in", OUT)
