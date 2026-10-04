"""Build one day of Detour content.

Fetches news + video + podcast feeds, asks Gemini for words / loop cards / news
bullets, validates the result and writes content/latest.json plus a dated copy.

Runs with the standard library only. If GEMINI_API_KEY is missing or Gemini
fails, words and loop cards come from pipeline/seed/ and news falls back to
raw headlines, so the app always gets a usable day.

    python pipeline/build_day.py            # build today (IST)
    python pipeline/build_day.py --dry-run  # print, don't write
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import datetime as dt
import email.utils
import html
import json
import os
import random
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PIPE = ROOT / "pipeline"
CONTENT = ROOT / "content"
HISTORY = PIPE / "history.json"
IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
UA = "Mozilla/5.0 (DetourDaily; personal use)"

WORDS_PER_DAY = 9
LOOP_PER_DAY = 8
LOOP_TYPES = ["slang", "psych", "acronym", "paradox", "meme"]
INDIA_BULLETS = (8, 10)
WORLD_BULLETS = (5, 6)


# ---------------------------------------------------------------- fetching

def fetch(url: str, timeout: int = 25) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def _local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def _child(el: ET.Element, name: str) -> ET.Element | None:
    for c in el:
        if _local(c.tag) == name:
            return c
    return None


def _text(el: ET.Element, name: str) -> str:
    c = _child(el, name)
    return (c.text or "").strip() if c is not None else ""


def _clean(s: str) -> str:
    s = re.sub(r"<[^>]+>", " ", html.unescape(s or ""))
    return re.sub(r"\s+", " ", s).strip()


def _date(s: str) -> dt.datetime | None:
    if not s:
        return None
    try:
        return dt.datetime.fromisoformat(s.replace("Z", "+00:00"))
    except ValueError:
        pass
    try:
        return email.utils.parsedate_to_datetime(s)
    except (TypeError, ValueError):
        return None


def parse_feed(raw: bytes) -> list[dict]:
    """Parse RSS 2.0 or Atom into a list of plain dicts."""
    root = ET.fromstring(raw)
    items = [e for e in root.iter() if _local(e.tag) in ("item", "entry")]
    out = []
    for it in items:
        link = _text(it, "link")
        if not link:
            le = _child(it, "link")
            link = le.get("href", "") if le is not None else ""
        enclosure = _child(it, "enclosure")
        thumb = ""
        group = _child(it, "group")  # YouTube media:group
        if group is not None:
            t = _child(group, "thumbnail")
            thumb = t.get("url", "") if t is not None else ""
        out.append({
            "title": _clean(_text(it, "title")),
            "link": link.strip(),
            "summary": _clean(_text(it, "description") or _text(it, "summary"))[:400],
            "date": _date(_text(it, "pubDate") or _text(it, "published") or _text(it, "updated")),
            "videoId": _text(it, "videoId"),
            "audio": enclosure.get("url", "") if enclosure is not None else "",
            "duration": _text(it, "duration"),
            "thumb": thumb,
        })
    return out


def fetch_many(jobs: dict[str, str]) -> dict[str, list[dict]]:
    """Fetch + parse many feeds in parallel. Failures become empty lists."""
    def one(key):
        try:
            return key, parse_feed(fetch(jobs[key]))
        except Exception as e:  # noqa: BLE001 - one bad feed must not kill the day
            print(f"  ! feed failed: {key}: {e}", file=sys.stderr)
            return key, []
    with cf.ThreadPoolExecutor(12) as ex:
        return dict(ex.map(one, jobs))


# ---------------------------------------------------------------- history

def load_history() -> dict:
    if HISTORY.exists():
        return json.loads(HISTORY.read_text(encoding="utf-8"))
    return {"words": [], "loop": [], "videos": [], "podcasts": []}


def slug(s: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


# ---------------------------------------------------------------- gemini

def gemini(prompt: str, key: str) -> dict | list | None:
    models = [os.environ.get("GEMINI_MODEL", "gemini-flash-latest"), "gemini-2.5-flash"]
    body = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"responseMimeType": "application/json", "temperature": 0.9},
        "safetySettings": [
            {"category": c, "threshold": "BLOCK_NONE"} for c in (
                "HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT")
        ],
    }
    for model in dict.fromkeys(models):
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
        req = urllib.request.Request(
            url, data=json.dumps(body).encode(),
            headers={"Content-Type": "application/json", "x-goog-api-key": key})
        for attempt in range(2):
            try:
                with urllib.request.urlopen(req, timeout=90) as r:
                    data = json.loads(r.read())
                text = data["candidates"][0]["content"]["parts"][0]["text"]
                return json.loads(text)
            except Exception as e:  # noqa: BLE001
                print(f"  ! gemini {model} attempt {attempt + 1}: {e}", file=sys.stderr)
    return None


def prompt(name: str, **kw) -> str:
    text = (PIPE / "prompts" / f"{name}.md").read_text(encoding="utf-8")
    for k, v in kw.items():
        text = text.replace("{{" + k + "}}", v)
    return text


# ---------------------------------------------------------------- sections

def build_words(key: str | None, hist: dict, rng: random.Random) -> list[dict]:
    seen = set(hist["words"])
    if key:
        got = gemini(prompt("words", count=str(WORDS_PER_DAY),
                            avoid=", ".join(hist["words"][-400:]) or "(none)"), key)
        words = [w for w in (got or []) if valid_word(w) and slug(w["word"]) not in seen]
        if len(words) >= 6:
            return words[:WORDS_PER_DAY]
    seed = json.loads((PIPE / "seed" / "words.json").read_text(encoding="utf-8"))
    pool = [w for w in seed if slug(w["word"]) not in seen] or seed
    rng.shuffle(pool)
    return pool[:WORDS_PER_DAY]


def valid_word(w) -> bool:
    return isinstance(w, dict) and all(isinstance(w.get(k), str) and w[k].strip()
                                       for k in ("word", "meaning", "insteadOf", "example"))


def build_loop(key: str | None, hist: dict, rng: random.Random) -> list[dict]:
    seen = set(hist["loop"])
    cards: list[dict] = []
    if key:
        got = gemini(prompt("loop", count=str(LOOP_PER_DAY),
                            avoid=", ".join(hist["loop"][-600:]) or "(none)"), key)
        cards = [c for c in (got or []) if valid_card(c) and slug(c["term"]) not in seen]
    if len(cards) < 5:
        seed = json.loads((PIPE / "seed" / "loop.json").read_text(encoding="utf-8"))
        pool = [c for c in seed if slug(c["term"]) not in seen] or seed
        rng.shuffle(pool)
        # round-robin across types so every day has variety
        by_type = {t: [c for c in pool if c["type"] == t] for t in LOOP_TYPES}
        cards = []
        while len(cards) < LOOP_PER_DAY and any(by_type.values()):
            for t in LOOP_TYPES:
                if by_type[t] and len(cards) < LOOP_PER_DAY:
                    cards.append(by_type[t].pop())
    for c in cards:
        c["id"] = slug(c["term"])
        c["nsfw"] = bool(c.get("nsfw", False))
        c.setdefault("tone", "casual")
    return cards[:LOOP_PER_DAY]


def valid_card(c) -> bool:
    return (isinstance(c, dict) and c.get("type") in LOOP_TYPES
            and all(isinstance(c.get(k), str) and c[k].strip()
                    for k in ("term", "meaning", "example", "context")))


def _similar(a: str, b: str) -> bool:
    wa = {w for w in re.findall(r"[a-z]{4,}", a.lower())}
    wb = {w for w in re.findall(r"[a-z]{4,}", b.lower())}
    return bool(wa and wb) and len(wa & wb) / min(len(wa), len(wb)) > 0.5


def headline_pool(feeds: dict[str, list[dict]], names: dict[str, str], now: dt.datetime) -> list[dict]:
    """Recent headlines, round-robin across sources, near-duplicates removed."""
    per_src = []
    for key, items in feeds.items():
        fresh = [i for i in items if i["title"] and (i["date"] is None or now - i["date"] < dt.timedelta(hours=36))]
        per_src.append([{**i, "source": names[key]} for i in fresh[:25]])
    pool: list[dict] = []
    for row in range(25):
        for src in per_src:
            if row < len(src) and not any(_similar(src[row]["title"], p["title"]) for p in pool):
                pool.append(src[row])
    return pool


def build_news(key: str | None, sources: dict, now: dt.datetime) -> dict:
    out = {}
    for region, (lo, hi) in (("india", INDIA_BULLETS), ("world", WORLD_BULLETS)):
        srcs = sources["news"][region]
        feeds = fetch_many({f"{region}{i}": s["url"] for i, s in enumerate(srcs)})
        names = {f"{region}{i}": s["name"] for i, s in enumerate(srcs)}
        pool = headline_pool(feeds, names, now)[:45]
        bullets: list[dict] = []
        if key and pool:
            listing = "\n".join(f"[{i}] ({p['source']}) {p['title']}: {p['summary'][:220]}"
                                for i, p in enumerate(pool))
            got = gemini(prompt("news", region="India" if region == "india" else "the world (outside India)",
                                lo=str(lo), hi=str(hi), headlines=listing), key)
            for b in (got or []):
                if isinstance(b, dict) and isinstance(b.get("text"), str) and isinstance(b.get("ref"), int) \
                        and 0 <= b["ref"] < len(pool):
                    p = pool[b["ref"]]
                    bullets.append({"text": b["text"].strip(), "source": p["source"], "url": p["link"]})
        if len(bullets) < lo:
            bullets = [{"text": p["title"], "source": p["source"], "url": p["link"]} for p in pool[:hi]]
        out[region] = bullets[:hi]
    return out


def build_videos(sources: dict, hist: dict, day: dt.date, now: dt.datetime) -> dict:
    chans = sources["channels"]
    # UULF = a channel's long-form uploads only (no Shorts, no lives)
    jobs = {c["id"]: f"https://www.youtube.com/feeds/videos.xml?playlist_id=UULF{c['id'][2:]}" for c in chans}
    feeds = fetch_many(jobs)
    seen = set(hist["videos"])
    by_cat: dict[str, list[dict]] = {}
    for c in chans:
        for it in feeds.get(c["id"], []):
            vid = it["videoId"]
            if not vid or vid in seen or it["date"] is None or now - it["date"] > dt.timedelta(days=150):
                continue
            by_cat.setdefault(c["category"], []).append({
                "id": vid,
                "title": it["title"],
                "channel": c["name"],
                "category": c["category"],
                "url": f"https://www.youtube.com/watch?v={vid}",
                "thumbnail": f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg",
                "published": it["date"].date().isoformat(),
            })
    for vids in by_cat.values():
        # freshest first, but at most 2 per channel so one prolific channel can't dominate
        vids.sort(key=lambda v: v["published"], reverse=True)
        count: dict[str, int] = {}
        capped = []
        for v in vids:
            count[v["channel"]] = count.get(v["channel"], 0) + 1
            if count[v["channel"]] <= 2:
                capped.append(v)
        vids[:] = capped
    cats = sources["categories"]
    start = day.toordinal() % len(cats)
    order = cats[start:] + cats[:start]
    picks = []
    for cat in order:
        if by_cat.get(cat):
            # a little randomness inside the category so it isn't always the newest upload
            options = by_cat[cat][:4]
            picks.append(random.Random(day.toordinal()).choice(options))
        if len(picks) == 5:
            break
    return {"hero": picks[0] if picks else None, "more": picks[1:]}


def _seconds(d: str) -> int | None:
    if not d:
        return None
    if d.isdigit():
        return int(d)
    parts = [int(p) for p in d.split(":") if p.isdigit()]
    secs = 0
    for p in parts:
        secs = secs * 60 + p
    return secs or None


def build_podcasts(sources: dict, hist: dict, day: dt.date) -> list[dict]:
    pods = sources["podcasts"]
    feeds = fetch_many({p["name"]: p["url"] for p in pods})
    seen = set(hist["podcasts"])
    start = day.toordinal() % len(pods)
    out = []
    for p in pods[start:] + pods[:start]:
        for it in feeds.get(p["name"], [])[:15]:
            if it["audio"] and it["audio"] not in seen:
                out.append({
                    "id": slug(p["name"] + "-" + it["title"])[:80],
                    "title": it["title"],
                    "show": p["name"],
                    "category": p["category"],
                    "audioUrl": it["audio"],
                    "link": it["link"],
                    "durationSec": _seconds(it["duration"]),
                })
                break
        if len(out) == 2:
            break
    return out


# ---------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    now = dt.datetime.now(dt.timezone.utc)
    day = now.astimezone(IST).date()
    key = os.environ.get("GEMINI_API_KEY") or None
    rng = random.Random(day.toordinal())
    sources = json.loads((PIPE / "sources.json").read_text(encoding="utf-8"))
    hist = load_history()
    print(f"Building {day} (gemini: {'on' if key else 'off'})")

    content = {
        "schema": 1,
        "date": day.isoformat(),
        "generatedAt": now.isoformat(timespec="seconds"),
        "words": build_words(key, hist, rng),
        "loop": build_loop(key, hist, rng),
        "news": build_news(key, sources, now),
        "videos": build_videos(sources, hist, day, now),
        "podcasts": build_podcasts(sources, hist, day),
    }

    problems = validate(content)
    if problems:
        print("Validation failed:\n  " + "\n  ".join(problems), file=sys.stderr)
        return 1
    summary = {k: len(v) if isinstance(v, list) else None for k, v in content.items()}
    print(f"  words={summary['words']} loop={summary['loop']} "
          f"india={len(content['news']['india'])} world={len(content['news']['world'])} "
          f"videos={1 + len(content['videos']['more'])} podcasts={summary['podcasts']}")

    if args.dry_run:
        print(json.dumps(content, indent=2, ensure_ascii=False)[:3000])
        return 0

    CONTENT.mkdir(exist_ok=True)
    text = json.dumps(content, indent=2, ensure_ascii=False)
    (CONTENT / "latest.json").write_text(text, encoding="utf-8")
    (CONTENT / f"{day.isoformat()}.json").write_text(text, encoding="utf-8")

    hist["words"] += [slug(w["word"]) for w in content["words"]]
    hist["loop"] += [c["id"] for c in content["loop"]]
    hist["videos"] += [v["id"] for v in [content["videos"]["hero"], *content["videos"]["more"]] if v]
    hist["podcasts"] += [p["audioUrl"] for p in content["podcasts"]]
    HISTORY.write_text(json.dumps(hist, indent=1, ensure_ascii=False), encoding="utf-8")
    print(f"Wrote content/latest.json and content/{day.isoformat()}.json")
    return 0


def validate(c: dict) -> list[str]:
    p = []
    if len(c["words"]) < 5:
        p.append(f"only {len(c['words'])} words")
    if len(c["loop"]) < 5:
        p.append(f"only {len(c['loop'])} loop cards")
    if len(c["news"]["india"]) < 3:
        p.append(f"only {len(c['news']['india'])} India bullets")
    if len(c["news"]["world"]) < 3:
        p.append(f"only {len(c['news']['world'])} world bullets")
    if not c["videos"]["hero"]:
        p.append("no hero video")
    return p


if __name__ == "__main__":
    sys.exit(main())
