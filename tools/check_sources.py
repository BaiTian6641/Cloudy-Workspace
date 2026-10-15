#!/usr/bin/env python3
"""Read-only bounded source reachability/drift check; never marks content reviewed."""
import argparse
import concurrent.futures
import hashlib
import json
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAX_BODY = 8 * 1024 * 1024


def inspect_source(source):
    row = dict(id=source['id'], requestedUrl=source['url'], checkedAt=datetime.now(timezone.utc).isoformat())
    request = urllib.request.Request(source['url'], headers={'User-Agent': 'Cloudy-Workspace-reference-check/0.1 (read-only documentation research)', 'Accept': 'text/html,text/plain,application/json;q=0.9,*/*;q=0.1'})
    try:
        with urllib.request.urlopen(request, timeout=12) as response:
            body = response.read(MAX_BODY + 1)
            truncated = len(body) > MAX_BODY
            row.update(httpStatus=response.status, finalUrl=response.url, contentType=response.headers.get('Content-Type'), bodySha256=hashlib.sha256(body[:MAX_BODY]).hexdigest(), hashedBytes=min(len(body), MAX_BODY), truncated=truncated, error=None)
    except urllib.error.HTTPError as exc:
        row.update(httpStatus=exc.code, finalUrl=exc.url, error=str(exc))
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        row.update(httpStatus=None, finalUrl=None, error=str(exc))
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--previous', type=Path)
    parser.add_argument('--only', help='Comma-separated source IDs')
    args = parser.parse_args()
    sources = json.loads((ROOT / 'docs/07-reference/sources.json').read_text())['sources']
    if args.only:
        wanted = set(args.only.split(','))
        unknown = wanted - {s['id'] for s in sources}
        if unknown:
            parser.error('Unknown source IDs: ' + ', '.join(sorted(unknown)))
        sources = [s for s in sources if s['id'] in wanted]
    previous = {}
    if args.previous:
        previous = {r['id']: r for r in json.loads(args.previous.read_text())['results']}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(inspect_source, sources))
    for row in results:
        old = previous.get(row['id'])
        row['driftCandidate'] = bool(old and row.get('bodySha256') and old.get('bodySha256') and row['bodySha256'] != old['bodySha256'])
        row['redirectChanged'] = bool(old and row.get('finalUrl') and old.get('finalUrl') and row['finalUrl'] != old['finalUrl'])
    reachable = sum(1 for r in results if r.get('httpStatus') and 200 <= r['httpStatus'] < 300)
    result = dict(schemaVersion=1, scope='reachability-and-raw-response-drift-only', checkedAt=datetime.now(timezone.utc).isoformat(), semanticReviewPerformed=False, summary=dict(total=len(results), reachable=reachable, errors=len(results)-reachable, driftCandidates=sum(r['driftCandidate'] for r in results)), results=results)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result['summary'], indent=2))
    for row in results:
        if row.get('error'):
            print(f"{row['id']}: {row['httpStatus']} {row['error']}")
    return 0 if reachable == len(results) else 1


if __name__ == '__main__':
    sys.exit(main())
