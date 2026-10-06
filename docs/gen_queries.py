"""Regenerates the <queries> package list in AndroidManifest.xml from assets/stalkerware_packages.csv."""
import re, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
csv = (root / "app/src/main/assets/stalkerware_packages.csv").read_text().splitlines()
pk = sorted({l.split(",")[0] for l in csv if l and not l.startswith("#") and re.fullmatch(r"[A-Za-z0-9._]+", l.split(",")[0])})
mf = root / "app/src/main/AndroidManifest.xml"
s = mf.read_text()
block = "\n".join(f'        <package android:name="{p}" />' for p in pk)
s = re.sub(r"(<action android:name=\"android.app.action.DEVICE_ADMIN_ENABLED\" /></intent>\n).*?(\n    </queries>)", lambda m: m.group(1) + block + m.group(2), s, flags=re.S)
mf.write_text(s)
print(len(pk), "packages written")
