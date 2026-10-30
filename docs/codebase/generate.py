"""Regenerate the searchable documentation index from Java source. Requires javalang."""
import json
import subprocess
from pathlib import Path

import javalang

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
SOURCE = ROOT / 'app/src/main/java'
sha = subprocess.check_output(['git', 'rev-parse', 'origin/main'], cwd=ROOT, text=True).strip()

def typename(value):
    if value is None:
        return 'void'
    if isinstance(value, str):
        return value
    name = value.name
    if getattr(value, 'sub_type', None):
        name += '.' + typename(value.sub_type)
    args = getattr(value, 'arguments', None)
    if args:
        name += '<' + ', '.join(typename(a.type) if getattr(a, 'type', None) else '?' for a in args) + '>'
    return name + '[]' * len(getattr(value, 'dimensions', []) or [])

entries = []
for path in sorted(SOURCE.rglob('*.java')):
    source = path.read_text(encoding='utf-8')
    tree = javalang.parse.parse(source)
    relative = path.relative_to(ROOT).as_posix()
    url = f'https://github.com/Rifat-Rashid/WorldDomination2-Android-Port/blob/{sha}/{relative}'
    file_entry = {'path': relative, 'package': tree.package.name if tree.package else '', 'types': [], 'url': url, 'lines': len(source.splitlines())}
    for _, node in tree.filter(javalang.tree.TypeDeclaration):
        if not isinstance(node, (javalang.tree.ClassDeclaration, javalang.tree.InterfaceDeclaration, javalang.tree.EnumDeclaration)):
            continue
        line = node.position.line if node.position else 1
        typ = {'name': node.name, 'kind': type(node).__name__.replace('Declaration','').lower(), 'line': line, 'modifiers': sorted(node.modifiers), 'fields': [], 'methods': [], 'constructors': [], 'enum_values': [], 'extends': typename(node.extends) if getattr(node,'extends',None) else None}
        if isinstance(node, javalang.tree.EnumDeclaration) and node.body:
            typ['enum_values'] = [v.name for v in node.body.constants]
        members = node.body if isinstance(node.body,list) else node.body.declarations
        field_names = {variable.name for member in members if isinstance(member, javalang.tree.FieldDeclaration) for variable in member.declarators}
        for member in members:
            if isinstance(member, javalang.tree.FieldDeclaration):
                for variable in member.declarators:
                    typ['fields'].append({'name': variable.name, 'type': typename(member.type), 'line': member.position.line if member.position else line, 'modifiers': sorted(member.modifiers)})
            elif isinstance(member, (javalang.tree.MethodDeclaration, javalang.tree.ConstructorDeclaration)):
                params = [{'name': p.name, 'type': typename(p.type), 'varargs': p.varargs} for p in member.parameters]
                signature = member.name + '(' + ', '.join(p['type'] + ('...' if p['varargs'] else '') + ' ' + p['name'] for p in params) + ')'
                item = {'name': member.name, 'signature': signature, 'returns': typename(member.return_type) if isinstance(member,javalang.tree.MethodDeclaration) else None, 'line': member.position.line if member.position else line, 'modifiers': sorted(member.modifiers), 'parameters': params, 'locals': [], 'field_references': [], 'calls': []}
                for _, local in member.filter(javalang.tree.VariableDeclaration):
                    for v in local.declarators:
                        item['locals'].append({'name': v.name, 'type': typename(local.type), 'line': local.position.line if local.position else item['line']})
                for _, local in member.filter(javalang.tree.CatchClauseParameter):
                    item['locals'].append({'name': local.name, 'type': '|'.join(local.types), 'line': local.position.line if local.position else item['line']})
                shadowed = {v['name'] for v in params + item['locals']}
                item['field_references'] = sorted({ref.member for _, ref in member.filter(javalang.tree.MemberReference) if ref.member in field_names and ref.member not in shadowed})
                item['calls'] = sorted({call.member for _, call in member.filter(javalang.tree.MethodInvocation)})
                typ['methods' if isinstance(member,javalang.tree.MethodDeclaration) else 'constructors'].append(item)
        file_entry['types'].append(typ)
    entries.append(file_entry)

tests = [p.relative_to(ROOT).as_posix() for p in sorted((ROOT/'app/src/test/java').rglob('*.java'))]
data = {'commit': sha, 'files': entries, 'tests': tests, 'counts': {'files': len(entries), 'types': sum(len(f['types']) for f in entries), 'methods': sum(len(t['methods'])+len(t['constructors']) for f in entries for t in f['types']), 'fields': sum(len(t['fields']) for f in entries for t in f['types']), 'locals': sum(len(m['locals']) for f in entries for t in f['types'] for m in t['methods']+t['constructors'])}}
(OUT/'api.json').write_text(json.dumps(data, indent=2)+'\n', encoding='utf-8')
(OUT/'api-data.js').write_text('window.WD2_API = '+json.dumps(data, separators=(',', ':'))+';\n', encoding='utf-8')
print(data['counts'])
