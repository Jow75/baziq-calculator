import subprocess
import time
import os
import sys

ADB_PATH = r"C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "adb-0978354327102255-ymLEd6._adb-tls-connect._tcp"

def run_adb(args, timeout=30):
    cmd = [ADB_PATH, "-s", DEVICE] + args
    res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    return res.stdout.strip() if res.stdout else "", res.stderr.strip() if res.stderr else "", res.returncode

def ensure_connected():
    for attempt in range(5):
        # Check if already in devices
        res = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True)
        if f"{DEVICE}\tdevice" in res.stdout:
            return True
        print(f"Connecting to {DEVICE} (attempt {attempt+1})...")
        conn_res = subprocess.run([ADB_PATH, "connect", DEVICE], capture_output=True, text=True)
        print("Connect output:", conn_res.stdout.strip(), conn_res.stderr.strip())
        time.sleep(1.0)
        res = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True)
        if f"{DEVICE}\tdevice" in res.stdout:
            return True
    return False

def take_screenshot(filename):
    if not ensure_connected():
        print("Error: Could not connect to device!")
        return False
    remote_path = "/sdcard/screen.png"
    out, err, code = run_adb(["shell", "screencap", "-p", remote_path])
    if code != 0:
        print("screencap failed:", err)
        return False
    out, err, code = run_adb(["pull", remote_path, filename])
    run_adb(["shell", "rm", remote_path])
    print(f"Screenshot successfully saved to {filename}")
    return True

if __name__ == "__main__":
    if len(sys.argv) > 1:
        cmd = sys.argv[1]
        if cmd == "connect":
            success = ensure_connected()
            print("Connected:", success)
        elif cmd == "devices":
            ensure_connected()
            res = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True)
            print(res.stdout)
        elif cmd == "screenshot":
            out_file = sys.argv[2] if len(sys.argv) > 2 else "screenshot.png"
            take_screenshot(out_file)
        elif cmd == "shell":
            ensure_connected()
            out, err, code = run_adb(["shell"] + sys.argv[2:])
            print(out)
            if err:
                print("Error:", err, file=sys.stderr)
    else:
        ensure_connected()
