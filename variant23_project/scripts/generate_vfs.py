"""Generate sample ZIP VFS archives (not committed as binary files)."""
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parent.parent / 'vfs'
root.mkdir(exist_ok=True)
examples = {
    'minimal.zip': {},
    'files.zip': {'readme.txt': 'hello\nworld\n',
                  'docs/notes.txt': 'one\ntwo\nthree\n'},
    'nested.zip': {'a/b/c/deep.txt': 'deep level\n',
                   'docs/notes.txt': 'one\ntwo\nthree\n',
                   'readme.txt': 'hello\nworld\n'},
}
for name, files in examples.items():
    with ZipFile(root / name, 'w') as archive:
        for path, content in files.items():
            archive.writestr(path, content)
    print(root / name)
