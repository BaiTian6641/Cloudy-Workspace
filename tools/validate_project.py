#!/usr/bin/env python3
"""Validate local planning consistency; this does not qualify devices or apps."""
import argparse
import copy
import hashlib
import json
import os
import re
import sys
from datetime import date
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
IGNORED = {'.git', '.agents', '.aws', '.codex', '.venv', '.cache', 'node_modules', '__pycache__', 'artifacts'}
INTAKE_HASH = '08b61fb0376634c1b6ffffbce9833f0d5fa053d5e4ee2212066716a330e45811'


def load(relative):
    return json.loads((ROOT / relative).read_text())


def files():
    result = []
    for directory, names, filenames in os.walk(ROOT):
        names[:] = [name for name in names if name not in IGNORED]
        result.extend(Path(directory) / name for name in filenames)
    return result


def luminance(color):
    values = [int(color[i:i+2], 16) / 255 for i in (1, 3, 5)]
    linear = [x / 12.92 if x <= .04045 else ((x + .055) / 1.055) ** 2.4 for x in values]
    return sum(x * weight for x, weight in zip(linear, (.2126, .7152, .0722)))


def contrast(foreground, background):
    a, b = sorted((luminance(foreground), luminance(background)))
    return (b + .05) / (a + .05)


class SpecimenParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = []
        self.links = []
        self.id_references = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if 'id' in attrs:
            self.ids.append(attrs['id'])
        for key in ('aria-controls', 'aria-labelledby', 'for'):
            self.id_references.extend(attrs.get(key, '').split())
        for key in ('href', 'src'):
            if key in attrs:
                self.links.append(attrs[key])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--schemas', action='store_true', help='Also validate draft schemas/examples and negative cases; requires jsonschema.')
    parser.add_argument('--report', type=Path, help='Write a machine-readable result to this path.')
    args = parser.parse_args()
    errors = []
    checks = {}
    all_files = files()

    def check(condition, message):
        if not condition:
            errors.append(message)

    json_files = [path for path in all_files if path.suffix == '.json']
    for path in json_files:
        try:
            json.loads(path.read_text())
        except (ValueError, UnicodeError) as exc:
            errors.append(f'{path.relative_to(ROOT)}: invalid JSON: {exc}')
    checks['jsonFiles'] = len(json_files)

    markdown = [path for path in all_files if path.suffix == '.md']
    local_links = 0
    for path in markdown:
        text = re.sub(r'```.*?```', '', path.read_text(), flags=re.S)
        for target in re.findall(r'!?\[[^\]]*\]\(([^\n]+?)\)', text):
            target = target.strip().strip('<>')
            parsed = urlsplit(target)
            if parsed.scheme or parsed.netloc or not parsed.path:
                continue
            local_links += 1
            destination = (path.parent / unquote(parsed.path)).resolve()
            check(destination.is_relative_to(ROOT) and destination.exists(), f'{path.relative_to(ROOT)}: broken/outside local link: {target}')
    checks.update(markdownFiles=len(markdown), localMarkdownLinks=local_links)

    sources = load('docs/07-reference/sources.json')
    source_ids = [s['id'] for s in sources['sources']]
    check(len(set(source_ids)) == len(source_ids), 'Duplicate source IDs')
    for source in sources['sources']:
        check(source['url'].startswith('https://'), f"Source {source['id']} is not HTTPS")
        check(source['reviewStatus'] in {'reviewed', 'located', 'javascript-required'}, f"Unknown source review state: {source['id']}")
        date.fromisoformat(source['checkedOn'])
    checks['officialSources'] = len(source_ids)

    backlog = load('docs/05-delivery/backlog.json')
    stage_ids = [stage['id'] for stage in backlog['stages']]
    stage_map = {s['id']: s for s in backlog['stages']}
    check(len(stage_map) == len(stage_ids), 'Duplicate stage IDs')
    cases = set(re.findall(r'\| (Q-[A-Z][0-9]{2}) \|', (ROOT / 'docs/06-validation/test-cases.md').read_text()))
    visited, active = set(), set()

    def visit(ident):
        if ident in active:
            errors.append(f'Cycle in stage dependencies at {ident}')
            return
        if ident in visited or ident not in stage_map:
            return
        active.add(ident)
        for dependency in stage_map[ident]['dependsOn']:
            check(dependency in stage_map, f'{ident}: missing dependency {dependency}')
            visit(dependency)
        active.remove(ident)
        visited.add(ident)

    for stage in backlog['stages']:
        visit(stage['id'])
        check(stage['status'] in {'planned', 'ready', 'in-progress', 'review', 'done', 'blocked'}, f"Invalid stage status {stage['id']}")
        check(0 < stage['effortDays']['low'] <= stage['effortDays']['high'], f"Invalid effort range {stage['id']}")
        check(bool(stage['tasks']) and bool(stage['deliverables']) and bool(stage['exitCriteria']), f"Incomplete stage {stage['id']}")
        for ident in stage['sourceIds']:
            check(ident in source_ids, f"{stage['id']}: unknown source {ident}")
        for ident in stage['acceptanceCases']:
            check(ident in cases, f"{stage['id']}: unknown acceptance case {ident}")
    for phase in backlog['phases']:
        phase_stages = [s for s in backlog['stages'] if s['phase'] == phase['id']]
        for bound in ('low', 'high'):
            check(sum(s['effortDays'][bound] for s in phase_stages) == phase['effortDays'][bound], f"{phase['id']}: effort sum drift")
        phase_path = ROOT / 'docs/05-delivery' / phase['document']
        check(phase_path.exists(), f"Missing phase file {phase['document']}")
        if phase_path.exists():
            documented = re.findall(r'^## (P[0-6]-S[0-9]+) —', phase_path.read_text(), flags=re.M)
            check(documented == [s['id'] for s in phase_stages], f"{phase['id']}: stage headings differ from backlog")
    checks.update(phases=len(backlog['phases']), stages=len(stage_ids), acceptanceCases=len(cases), dependencyGraph='acyclic' if not any('Cycle' in e for e in errors) else 'invalid', effortDays={bound: sum(s['effortDays'][bound] for s in backlog['stages']) for bound in ('low', 'high')})

    apis = load('docs/03-platform/googlebook/api-tracker.json')['apis']
    check(len({a['id'] for a in apis}) == len(apis), 'Duplicate API IDs')
    for api in apis:
        check(api['stage'] in stage_ids, f"{api['id']}: unknown stage {api['stage']}")
        for ident in api['sourceIds']:
            check(ident in source_ids, f"{api['id']}: unknown source {ident}")
        check(bool(api['fallback']) and bool(api['experiment']), f"{api['id']}: missing fallback or experiment")
    checks['apiRecords'] = len(apis)

    tokens = load('shared/design-system/tokens.json')
    ratios = []
    for theme, colors in tokens['themes'].items():
        for key, value in {**colors, **tokens['ansi']}.items():
            check(bool(re.fullmatch(r'#[0-9A-Fa-f]{6}', value)), f'{theme}/{key}: invalid color')
        pairs = tokens['contrastPairs'] + [dict(foreground='ansi:' + key, background='terminal-background', minimum=tokens['ansiMinimumContrast']) for key in tokens['ansi']]
        for pair in pairs:
            fg_key, bg_key = pair['foreground'], pair['background']
            fg = tokens['ansi'][fg_key[5:]] if fg_key.startswith('ansi:') else colors[fg_key]
            ratio = contrast(fg, colors[bg_key])
            ratios.append(dict(theme=theme, foreground=fg_key, background=bg_key, ratio=round(ratio, 3), minimum=pair['minimum']))
            check(ratio >= pair['minimum'], f'{theme}: {fg_key}/{bg_key} contrast {ratio:.2f} < {pair["minimum"]}')
    checks['contrastPairs'] = len(ratios)
    checks['lowestContrastRatio'] = min(r['ratio'] for r in ratios)
    from generate_design_tokens import DESTINATION, generated_css
    check(DESTINATION.exists() and DESTINATION.read_text() == generated_css(), 'Generated CSS tokens differ from canonical data')

    html_path = ROOT / 'docs/04-design/preview.html'
    html = SpecimenParser()
    html.feed(html_path.read_text())
    check(len(html.ids) == len(set(html.ids)), 'Duplicate HTML IDs in specimen')
    for ident in html.id_references:
        check(ident in html.ids, f'Specimen has unresolved ARIA/label ID {ident}')
    for target in html.links:
        parsed = urlsplit(target)
        if not parsed.scheme and not parsed.netloc and parsed.path:
            check((html_path.parent / parsed.path).exists(), f'Specimen missing local asset {target}')
        if target.startswith('#'):
            check(target[1:] in html.ids, f'Specimen missing SVG/anchor target {target}')
    js = (ROOT / 'docs/04-design/preview.js').read_text()
    for ident in re.findall(r"byId\('([^']+)'\)", js):
        check(ident in html.ids, f'JS refers to absent HTML ID {ident}')
    checks['specimenIds'] = len(html.ids)

    intake = ROOT / 'docs/intake/GoogleBook_Self_Cloud_Workspace_Plan.md'
    check(hashlib.sha256(intake.read_bytes()).hexdigest() == INTAKE_HASH, 'Preserved intake SHA-256 differs from original')
    checks['intakeHash'] = 'verified' if not any('SHA-256' in e for e in errors) else 'invalid'

    if args.schemas:
        try:
            from jsonschema import Draft202012Validator, FormatChecker
        except ImportError:
            errors.append('Install requirements-dev.txt to run --schemas')
        else:
            validators, examples = {}, {}
            for name in ('handoff', 'capabilities', 'event'):
                schema = load(f'protocol/schemas/{name}.schema.json')
                example = load(f'protocol/examples/{name}.example.json')
                Draft202012Validator.check_schema(schema)
                validator = Draft202012Validator(schema, format_checker=FormatChecker())
                for error in validator.iter_errors(example):
                    errors.append(f'{name} example: {error.message}')
                validators[name], examples[name] = validator, example
            invalid = []
            value = copy.deepcopy(examples['handoff']); value['command'] = 'forbidden'; invalid.append(('handoff', value, 'command field'))
            value = copy.deepcopy(examples['handoff']); value['action'] = 'execute'; invalid.append(('handoff', value, 'executable action'))
            value = copy.deepcopy(examples['capabilities']); value['protocolVersion']['major'] = 2; invalid.append(('capabilities', value, 'unknown major'))
            value = copy.deepcopy(examples['capabilities']); value['platform'] = 'android'; invalid.append(('capabilities', value, 'invalid host platform'))
            value = copy.deepcopy(examples['event']); value['sequence'] = -1; invalid.append(('event', value, 'invalid sequence'))
            value = copy.deepcopy(examples['event']); value['eventId'] = 'invalid'; invalid.append(('event', value, 'invalid UUID'))
            for name, value, reason in invalid:
                check(not validators[name].is_valid(value), f'{name}: accepted {reason}')
            checks['schemas'] = dict(validSchemas=3, validExamples=3, rejectedInvalidCases=len(invalid))
    else:
        checks['schemas'] = 'not requested; use --schemas'

    result = dict(status='passed' if not errors else 'failed', scope='planning-assets-only', checks=checks, errors=errors, contrastDetails=ratios)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key != 'contrastDetails'}, indent=2))
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
