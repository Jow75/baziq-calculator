import subprocess

ADB = r"C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEV = "adb-0978354327102255-ymLEd6._adb-tls-connect._tcp"

def query_db():
    for f in ["omnicalc_local.db", "omnicalc_local.db-wal", "omnicalc_local.db-shm"]:
        cmd = [ADB, "-s", DEV, "exec-out", "run-as", "com.omnicalc.platform", "cat", f"databases/{f}"]
        res = subprocess.run(cmd, capture_output=True)
        with open(f, "wb") as out:
            out.write(res.stdout)
    
    import sqlite3
    conn = sqlite3.connect("omnicalc_local.db")
    cursor = conn.cursor()
    cursor.execute("SELECT currencyCode, orderIndex, isPinned FROM currency_workspace_items ORDER BY orderIndex ASC;")
    rows = cursor.fetchall()
    print("currency_workspace_items in SQLite (count = {}):".format(len(rows)))
    for row in rows:
        print(" ", row)
    conn.close()

if __name__ == "__main__":
    query_db()
