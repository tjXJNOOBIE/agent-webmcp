from pathlib import Path

ROOT = Path(__file__).resolve().parent
variants = {
    'A': ROOT / 'dashboard-a.html',
    'B': ROOT / 'dashboard-b.html',
    'C': ROOT / 'dashboard-c.html',
}
required = ['NO_AUTH', 'WebMCP', 'DESIGN FIXTURE', 'document.modelContext']
layouts = {'A': 'layout-a', 'B': 'b-workbench', 'C': 'c-workspace'}

for name, path in variants.items():
    text = path.read_text(encoding='utf-8')
    missing = [token for token in required if token not in text]
    if missing:
        raise SystemExit(f'{path.name}: missing required markers {missing}')
    if layouts[name] not in text:
        raise SystemExit(f'{path.name}: missing distinct layout marker {layouts[name]}')
    if 'http://' in text or 'https://' in text:
        raise SystemExit(f'{path.name}: external runtime asset/reference detected')
    if '<main' not in text:
        raise SystemExit(f'{path.name}: semantic <main> missing')
    if 'design-only' not in text.lower():
        raise SystemExit(f'{path.name}: design-only fixture boundary missing')

css = (ROOT / 'styles.css').read_text(encoding='utf-8')
for marker in ('.variant-a', '.variant-b', '.variant-c'):
    if marker not in css:
        raise SystemExit(f'styles.css: missing {marker}')

print('dashboard design validation passed: A, B, C are distinct, self-contained, and fixture-marked')
