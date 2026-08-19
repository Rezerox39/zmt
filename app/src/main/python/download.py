import yt_dlp
import json
import sys


def download(quickjs_bin: str, video_id: str) -> str:
    """Resolve a YouTube video ID to a playable stream URL using yt-dlp.
    
    yt-dlp handles ALL the hard stuff:
    - Signature deobfuscation (cipher)
    - N-parameter transformation (throttle avoidance)
    - PO token generation
    - Format selection
    - Client rotation
    """
    opts = {
        # Pick the best audio-only format, or best combined if no audio-only
        "format": "bestaudio[acodec!=none]/bestaudio/best",
        # Request format info so we get url, http_headers, etc.
        "skip_download": True,
        # Quiet output to avoid polluting our logs
        "quiet": True,
        "no_warnings": True,
        # Use geo bypass for region-locked content
        "geo_bypass": True,
        # Don't check certificates (some proxies need this)
        "no_check_certificates": True,
        # Extractor args to try different clients
        "extractor_args": {
            "youtube": {
                "player_client": ["web", "android", "ios", "tv"],
                "player_skip": ["webpage"],
            }
        },
        # HTTP headers for better compatibility
        "http_headers": {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Accept-Language": "en-US,en;q=0.9",
            "Accept": "*/*",
        },
        # Source address preference
        "source_address": "0.0.0.0",
        # Socket timeout
        "socket_timeout": 30,
    }

    if quickjs_bin:
        opts["js_runtimes"] = {"quickjs": {"path": quickjs_bin}}

    try:
        ydl = yt_dlp.YoutubeDL(opts)
        info = ydl.extract_info(video_id, download=False)
        
        if info is None:
            return json.dumps({"error": "extract_info returned None"})
        
        # Build a clean response with all the info we need
        result = {
            "id": info.get("id", video_id),
            "title": info.get("title", ""),
            "url": info.get("url"),
            "ext": info.get("ext", "mp4"),
            "filesize": info.get("filesize") or info.get("filesize_approx") or 0,
            "format_id": info.get("format_id", ""),
            "acodec": info.get("acodec", ""),
            "abr": info.get("abr", 0),
            "tbr": info.get("tbr", 0),
            "duration": info.get("duration", 0),
            # Include all available formats for fallback
            "formats": [],
        }
        
        # Process each format
        for fmt in info.get("formats", []):
            fmt_entry = {
                "format_id": fmt.get("format_id", ""),
                "url": fmt.get("url"),
                "ext": fmt.get("ext", ""),
                "acodec": fmt.get("acodec", ""),
                "vcodec": fmt.get("vcodec", "none"),
                "abr": fmt.get("abr", 0),
                "tbr": fmt.get("tbr", 0),
                "filesize": fmt.get("filesize") or 0,
                "format_note": fmt.get("format_note", ""),
                "protocol": fmt.get("protocol", ""),
                "http_headers": fmt.get("http_headers", {}),
            }
            result["formats"].append(fmt_entry)
        
        # If top-level URL is missing, find the best audio format with a URL
        if not result["url"]:
            for fmt in result["formats"]:
                if fmt["url"] and fmt["vcodec"] == "none":
                    result["url"] = fmt["url"]
                    result["format_id"] = fmt["format_id"]
                    result["ext"] = fmt["ext"]
                    result["filesize"] = fmt["filesize"]
                    result["acodec"] = fmt["acodec"]
                    result["abr"] = fmt["abr"]
                    break
            # If still no URL, try any format with a URL
            if not result["url"]:
                for fmt in result["formats"]:
                    if fmt["url"]:
                        result["url"] = fmt["url"]
                        result["format_id"] = fmt["format_id"]
                        result["ext"] = fmt["ext"]
                        result["filesize"] = fmt["filesize"]
                        break
        
        return json.dumps(result, indent=4)
        
    except yt_dlp.utils.DownloadError as e:
        return json.dumps({"error": str(e), "id": video_id})
    except Exception as e:
        return json.dumps({"error": f"{type(e).__name__}: {e}", "id": video_id})


def upgrade(package_name):
    try:
        import ensurepip
        ensurepip.bootstrap()
    except Exception as e:
        print(f"Error running ensurepip: {e}")
    try:
        import pip
        from pip._internal import main as pip_main
        pip_main(["install", "--upgrade", package_name])
        print(f"Successfully upgraded {package_name}")
    except Exception as e:
        print(f"Error upgrading package {package_name}: {e}")
