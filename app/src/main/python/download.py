import yt_dlp
import json


def download(quickjs_bin: str, video_id: str) -> str:
    opts = {
        "format": "bestaudio/best",
        "http_headers": {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Accept-Language": "en-US,en;q=0.9",
        },
        "no_check_certificates": True,
        "geo_bypass": True,
        "extract_flat": False,
    }

    if quickjs_bin:
        opts["js_runtimes"] = {"quickjs": {"path": quickjs_bin}}

    return json.dumps(
        yt_dlp.YoutubeDL(opts).extract_info(video_id, download=False), indent=4
    )


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
