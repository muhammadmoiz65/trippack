"""Records the TripPack demo screencast (about 1.5 minutes) with subtitles.

Drives the real app in a browser (Playwright), shows a subtitle bar on the
page for every step, records desktop and phone views and joins them into one
MP4 with ffmpeg (from the imageio-ffmpeg package).

    python tools/screencast.py   (app running on localhost:8080, empty data/ folder recommended)
Output: docs/screencast/trippack_demo.mp4
"""
import shutil
import subprocess
from datetime import date, timedelta
from pathlib import Path

import imageio_ffmpeg
from playwright.sync_api import sync_playwright

BASE = "http://localhost:8080"
OUT = Path(__file__).resolve().parent.parent / "docs" / "screencast"
RAW = OUT / "raw"
SIZE = {"width": 1280, "height": 720}

SUBTITLE_JS = """text => {
  let bar = document.getElementById('demo-subtitle');
  if (!bar) {
    bar = document.createElement('div');
    bar.id = 'demo-subtitle';
    bar.style.cssText = 'position:fixed;left:50%;bottom:24px;transform:translateX(-50%);max-width:88%;' +
      'background:rgba(22,49,63,.92);color:#fff;font:600 20px system-ui,Segoe UI,sans-serif;' +
      'padding:12px 20px;border-radius:10px;z-index:9999;text-align:center;line-height:1.35;pointer-events:none';
    document.body.appendChild(bar);
  }
  bar.textContent = text;
}"""


def say(page, text, seconds):
    """Shows a subtitle and waits so the viewer can read it."""
    page.evaluate(SUBTITLE_JS, text)
    page.wait_for_timeout(int(seconds * 1000))


def slow_type(page, selector, text):
    page.click(selector)
    for ch in text:
        page.keyboard.type(ch)
        page.wait_for_timeout(110)


def new_trip(page, city, start, end, trip_type):
    page.click("#new-trip")
    page.wait_for_timeout(500)
    slow_type(page, "#destination", city)
    page.wait_for_selector("#suggestions li")
    page.wait_for_timeout(1200)
    page.locator("#suggestions li").first.click()
    page.fill("#start", start.isoformat())
    page.fill("#end", end.isoformat())
    page.select_option("#type", trip_type)
    page.wait_for_timeout(1000)
    page.click("#trip-form button[type=submit]")
    page.wait_for_selector(".day")


def record_desktop(browser):
    ctx = browser.new_context(viewport=SIZE, record_video_dir=str(RAW), record_video_size=SIZE)
    page = ctx.new_page()
    page.on("dialog", lambda d: d.accept())
    page.goto(BASE)
    today = date.today()

    say(page, "TripPack builds a packing list from the weather at your destination.", 4)
    say(page, "Step 1: create a trip. The destination search runs through the Spring Boot back-end.", 1)
    new_trip(page, "Lisbon", today + timedelta(days=4), today + timedelta(days=8), "BEACH")
    say(page, "The trip is saved in the H2 database. The back-end loads the forecast from Open-Meteo.", 5)
    say(page, "Step 2: generate the list. Java rules use the weather, the number of nights and the trip type.", 2)
    page.click("#generate")
    page.wait_for_selector(".item")
    page.wait_for_timeout(2500)
    say(page, "Every item shows why it is on the list.", 3)
    page.mouse.wheel(0, 450)
    page.wait_for_timeout(2500)

    say(page, "Step 3: tick items while packing. Every change is saved through the REST API.", 1)
    boxes = page.locator(".item input[type=checkbox]")
    for i in range(3):
        boxes.nth(i).check()
        page.wait_for_timeout(700)
    page.locator(".qty button", has_text="+").nth(3).click()
    page.wait_for_timeout(1500)

    say(page, "You can also add your own items. They stay when the list is generated again.", 1)
    page.locator("#add-item").scroll_into_view_if_needed()
    slow_type(page, "#add-item input[name=name]", "Travel adapter")
    page.select_option("#add-item select", "TECH")
    page.click("#add-item button")
    page.wait_for_timeout(2500)

    say(page, "Trips more than 16 days ahead use last year's weather as typical weather.", 1)
    page.mouse.wheel(0, -3000)
    new_trip(page, "Oslo", today + timedelta(days=60), today + timedelta(days=64), "HIKING")
    page.wait_for_timeout(1500)
    page.click("#generate")
    page.wait_for_selector(".item")
    say(page, "Cold and rain in Oslo add a warm coat, gloves, a hat, a rain jacket and an umbrella.", 4)
    page.mouse.wheel(0, 650)
    page.wait_for_timeout(3000)
    path = page.video.path()
    ctx.close()
    return Path(path)


def record_phone(browser):
    phone = {"width": 390, "height": 720}
    ctx = browser.new_context(viewport=phone, device_scale_factor=1, is_mobile=True, has_touch=True,
                              record_video_dir=str(RAW), record_video_size=phone)
    page = ctx.new_page()
    page.goto(BASE)
    page.wait_for_selector(".day")
    say(page, "The layout is responsive. This is the phone view.", 4)
    page.mouse.wheel(0, 500)
    page.wait_for_timeout(2000)
    say(page, "Weather cards and the list stack in one column.", 2)
    page.locator(".item input[type=checkbox]").nth(4).check()
    page.wait_for_timeout(1500)
    page.mouse.wheel(0, 600)
    page.wait_for_timeout(2500)
    say(page, "TripPack, Muhammad Moiz, DLBCSPJWD01", 3)
    path = page.video.path()
    ctx.close()
    return Path(path)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    if RAW.exists():
        shutil.rmtree(RAW)
    with sync_playwright() as p:
        browser = p.chromium.launch()
        desktop = record_desktop(browser)
        phone = record_phone(browser)
        browser.close()

    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    out = OUT / "trippack_demo.mp4"
    # Phone clip is centred on a dark 1280x720 background, then both clips are joined.
    filters = ("[0:v]fps=25,scale=1280:720,setsar=1[a];"
               "[1:v]fps=25,pad=1280:720:(ow-iw)/2:0:color=0x16313f,setsar=1[b];"
               "[a][b]concat=n=2:v=1:a=0[v]")
    subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", str(desktop), "-i", str(phone),
                    "-filter_complex", filters, "-map", "[v]", "-c:v", "libx264", "-pix_fmt", "yuv420p",
                    "-crf", "26", "-preset", "slow", "-movflags", "+faststart", str(out)], check=True)
    shutil.rmtree(RAW)
    print("wrote", out, round(out.stat().st_size / 1e6, 1), "MB")
