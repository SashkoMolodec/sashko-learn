-- Before the switch to the Obsidian REST API, notes and attachments were keyed by absolute
-- filesystem paths (e.g. /Users/okravch/my/sl/notes/topics/foo.md). The API addresses files
-- by vault-relative paths (topics/foo.md, img/bar.png). Rewrite rows migrated from the laptop
-- dump so the first API-based /sync matches existing notes instead of recreating duplicates.

UPDATE notes
SET file_path = substring(file_path FROM length('/Users/okravch/my/sl/notes/') + 1)
WHERE file_path LIKE '/Users/okravch/my/sl/notes/%';

UPDATE attachments
SET file_path = substring(file_path FROM length('/Users/okravch/my/sl/notes/') + 1)
WHERE file_path LIKE '/Users/okravch/my/sl/notes/%';

UPDATE ai_notes
SET file_path = substring(file_path FROM length('/Users/okravch/my/sl/notes/') + 1)
WHERE file_path LIKE '/Users/okravch/my/sl/notes/%';
