"""Create a readable, auditable HTML view of actual Maven/Surefire results."""
from pathlib import Path
import hashlib
import html
import json
import re
import shutil
import sys
import xml.etree.ElementTree as ET


def read_text(path):
    data = path.read_bytes()
    encoding = 'utf-16' if data[:2] in (b'\xff\xfe', b'\xfe\xff') else 'utf-8-sig'
    try:
        return data.decode(encoding)
    except UnicodeDecodeError:
        return data.decode('cp1252')


def main():
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    metadata = json.loads((root / 'lab.json').read_text(encoding='utf-8'))
    evidence = root / 'evidence'
    junit = evidence / 'junit'
    junit.mkdir(parents=True, exist_ok=True)
    source_xml = sorted(root.glob('**/target/surefire-reports/TEST-*.xml'))
    for source in source_xml:
        shutil.copy2(source, junit / source.name)
    xml_files = sorted(junit.glob('TEST-*.xml'))
    if not xml_files:
        raise SystemExit('No genuine Surefire XML reports found')
    rows = []
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for file in xml_files:
        suite = ET.parse(file).getroot()
        for name in totals:
            totals[name] += int(suite.get(name, '0'))
        for case in suite.findall('testcase'):
            status = 'Réussi'
            for tag, label in [('failure', 'Échec'), ('error', 'Erreur'), ('skipped', 'Ignoré')]:
                if case.find(tag) is not None:
                    status = label
            rows.append((case.get('classname', ''), case.get('name', ''), case.get('time', ''), status))
    if totals['failures'] or totals['errors']:
        raise SystemExit(f'Refusing to report a passing run: {totals}')
    demo = read_text(evidence / 'demo-output.txt')
    tests = read_text(evidence / 'test-output.txt')
    if 'BUILD SUCCESS' not in demo or 'BUILD SUCCESS' not in tests:
        raise SystemExit('Both logs must contain actual Maven BUILD SUCCESS')
    # Preserve exactly the application's top-level output. Hibernate multiline SQL
    # is indented; JUL/SLF4J/Maven lines are excluded from this compact excerpt.
    excerpt = []
    for line in demo.splitlines():
        if not line.strip() or line.startswith((' ', '\t', '[', 'Hibernate:', 'INFO:', 'WARNING:', 'WARN:', 'ERROR:', 'SEVERE:', 'SLF4J:', 'Picked up ', 'java.', 'javax.', 'org.')):
            continue
        if re.match(r'^[A-Z][a-z]{2} \d{1,2}, \d{4} ', line):
            continue
        if re.match(r'^(order by|group by|union|select|from|where|into|values|set)$', line.strip(), re.IGNORECASE):
            continue
        excerpt.append(line)
    test_summary = [line for line in tests.splitlines() if 'Tests run:' in line or 'BUILD SUCCESS' in line or 'Finished at:' in line]
    hashes = {}
    for file in sorted(evidence.glob('*.txt')):
        hashes[file.name] = hashlib.sha256(file.read_bytes()).hexdigest()
    for file in xml_files:
        hashes['junit/' + file.name] = hashlib.sha256(file.read_bytes()).hexdigest()
    (evidence / 'checksums.json').write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf-8')
    def escape(value):
        return html.escape(str(value))
    test_rows = ''.join('<tr><td>' + escape(name) + '<br><span>' + escape(group) + '</span></td><td>' + escape(status) + '</td><td>' + escape(seconds) + ' s</td></tr>' for group, name, seconds, status in rows)
    summary = f"{totals['tests']} tests · {totals['failures']} échecs · {totals['errors']} erreurs · {totals['skipped']} ignorés"
    links = ''.join(f'<li><a href="{escape(name)}">{escape(name)}</a> <code>{digest}</code></li>' for name, digest in hashes.items())
    document = f'''<!doctype html>
<html lang="fr"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>{escape(metadata['title'])} — Résultats</title>
<style>
* {{ box-sizing: border-box; }}
body {{ margin: 0; background: #faf8f5; color: #292524; font: 15px/1.55 "Consolas", monospace; }}
main {{ max-width: 1180px; margin: 0 auto; padding: 32px; }}
header {{ border-bottom: 1px solid #d6d3d1; padding-bottom: 16px; margin-bottom: 24px; }}
h1 {{ font-size: 24px; line-height: 1.35; margin: 0 0 8px; }}
h2 {{ font-size: 18px; margin: 0 0 12px; }}
p {{ margin: 8px 0; }} a {{ color: #805015; }}
section {{ background: #ffffff; border: 1px solid #d6d3d1; padding: 24px; margin: 0 0 24px; }}
pre {{ margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; font: 15px/1.65 "Consolas", monospace; }}
table {{ border-collapse: collapse; width: 100%; }}
th,td {{ text-align: left; padding: 10px 12px; border-bottom: 1px solid #e7e5e4; vertical-align: top; }}
th {{ font-weight: 600; }} td span {{ color: #57534e; font-size: 12px; }}
td:nth-child(2) {{ color: #365314; white-space: nowrap; }}
.note {{ color: #57534e; font-size: 13px; }}
ul {{ padding-left: 20px; }} li {{ margin: 12px 0; }} code {{ display: block; overflow-wrap: anywhere; font-size: 11px; }}
</style></head><body><main>
<header><h1>{escape(metadata['title'])}</h1><p>MLIAEdu · Hibernate &amp; JPA · Lab {metadata['lab_id']}</p><p>{escape(summary)}</p></header>
<section id="results"><h2>Résultats de la démonstration</h2><p class="note">Extrait exact de la sortie de l'application; les logs techniques complets sont dans <a href="demo-output.txt">demo-output.txt</a>.</p><pre>{escape(chr(10).join(excerpt))}</pre></section>
<section id="tests"><h2>Tests de persistance</h2><p>{escape(summary)}</p><table><thead><tr><th>Scénario / classe</th><th>Résultat</th><th>Durée mesurée</th></tr></thead><tbody>{test_rows}</tbody></table><p class="note">Données lues directement dans les fichiers XML Surefire de cette exécution.</p><pre>{escape(chr(10).join(test_summary))}</pre></section>
<section><h2>Sources et intégrité</h2><p class="note">Capture navigateur de résultats réellement exécutés. Chaque fichier source est conservé avec son SHA-256; les durées peuvent varier lors d'une nouvelle exécution.</p><ul>{links}</ul></section>
</main></body></html>'''
    (evidence / 'report.html').write_text(document, encoding='utf-8')
    (evidence / 'summary.json').write_text(json.dumps(dict(metadata, **totals), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(repository=root.name, **totals)))


if __name__ == '__main__':
    main()
