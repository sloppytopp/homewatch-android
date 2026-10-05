# Security policy
Please report vulnerabilities privately using GitHub's "Report a vulnerability" (Security tab) rather than a public issue. You'll get an acknowledgement within a week.

Scope notes: Homewatch has no network access, so the main risks are local - for example parsing hostile radio data (Remote ID, Bluetooth advertisements, Wi-Fi names), the PIN/discreet-mode features, and exported files. Radio input is treated as attacker-controlled; spreadsheet formula injection in the WiGLE export is neutralised.
