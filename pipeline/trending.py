"""'Trending right now': what India is suddenly searching for and what's blowing up on Reddit.

The goal is the stuff people hear about from reels first. Signals:
  - Google Trends India daily RSS (search spikes, each with a few news links)
  - top-of-the-day posts from a few big Reddit communities (RSS; the JSON API blocks bots)
Gemini merges them into 5-6 English bullets saying what happened and why everyone's talking.
"""
from __future__ import annotations

import html
import re
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
from typing import Callable

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
SUBREDDITS = ["india", "IndiaSpeaks", "worldnews", "technology"]
ATOM = "{http://www.w3.org/2005/Atom}"


def _get(url: str) -> bytes:
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=25).read()


def _traffic(s: str) -> int:
    digits = re.sub(r"[^\d]", "", s or "")
    return int(digits) if digits else 0


def google_trends() -> list[dict]:
    root = ET.fromstring(_get("https://trends.google.com/trending/rss?geo=IN"))
    out = []
    for it in root.iter("item"):
        fields = {c.tag.rsplit("}", 1)[-1]: (c.text or "").strip() for c in it}
        news = []
        for n in it:
            if n.tag.endswith("news_item"):
                nf = {c.tag.rsplit("}", 1)[-1]: (c.text or "").strip() for c in n}
                news.append({"title": html.unescape(nf.get("news_item_title", "")),
                             "url": nf.get("news_item_url", ""), "source": nf.get("news_item_source", "")})
        out.append({"term": fields.get("title", ""), "traffic": _traffic(fields.get("approx_traffic", "")), "news": news})
    out.sort(key=lambda t: t["traffic"], reverse=True)
    return out


def reddit_top(sub: str, n: int = 8) -> list[dict]:
    root = ET.fromstring(_get(f"https://www.reddit.com/r/{sub}/top/.rss?t=day"))
    out = []
    for e in root.findall(f"{ATOM}entry")[:n]:
        title = e.find(f"{ATOM}title")
        link = e.find(f"{ATOM}link")
        out.append({"title": html.unescape(title.text or "") if title is not None else "",
                    "url": link.get("href", "") if link is not None else "", "sub": sub})
    return out


def build_trending(key: str | None, gemini: Callable, prompt: Callable) -> list[dict]:
    try:
        trends = google_trends()[:25]
    except Exception as e:  # noqa: BLE001
        print(f"  ! google trends failed: {e}", file=sys.stderr)
        trends = []
    posts: list[dict] = []
    for sub in SUBREDDITS:
        for attempt in range(2):
            try:
                posts += reddit_top(sub)
                break
            except Exception as e:  # noqa: BLE001
                print(f"  ! reddit r/{sub} attempt {attempt + 1}: {e}", file=sys.stderr)
                time.sleep(8)
        time.sleep(3)  # Reddit rate-limits quick bursts
    print(f"  trending: {len(trends)} google trends, {len(posts)} reddit posts")

    # One list of numbered signals, so Gemini can point back at a source link.
    signals: list[dict] = []
    for t in trends:
        head = t["news"][0] if t["news"] else {"title": "", "url": "", "source": ""}
        signals.append({"line": f"[search spike, {t['traffic']:,}+ searches] \"{t['term']}\": "
                                + " / ".join(n["title"] for n in t["news"][:2]),
                        "url": head["url"], "source": head["source"] or "Google Trends"})
    for p in posts:
        signals.append({"line": f"[top post, r/{p['sub']}] {p['title']}", "url": p["url"], "source": f"r/{p['sub']}"})
    if not signals:
        return []

    if key:
        listing = "\n".join(f"[{i}] {s['line']}" for i, s in enumerate(signals))
        got = gemini(prompt("trending", signals=listing), key)
        out = []
        for g in got if isinstance(got, list) else []:
            if isinstance(g, dict) and isinstance(g.get("ref"), int) and 0 <= g["ref"] < len(signals) \
                    and isinstance(g.get("text"), str):
                s = signals[g["ref"]]
                out.append({"text": g["text"].strip(), "why": str(g.get("why", "")).strip(),
                            "url": s["url"], "source": s["source"]})
        if len(out) >= 3:
            return out[:6]

    # No Gemini: the biggest English-headlined search spikes, as-is.
    out = []
    for t in trends:
        n = next((n for n in t["news"] if n["title"].isascii()), None)
        if n and all(n["title"] != o["text"] for o in out):
            out.append({"text": n["title"], "why": f"{t['traffic']:,}+ searches in India today",
                        "url": n["url"], "source": n["source"]})
        if len(out) == 5:
            break
    return out
