---
name: room-migration
description: Add or change a Room table or column in this app safely - entity, DAO, version bump, a hand-written Migration whose SQL matches the exported schema exactly, and the check that proves it. Use whenever data/local/AppDatabase.kt, an @Entity, or app/schemas/ is touched.
---

# Room migration (never destructive)

The database holds surveyed POIs, walked tracks, SOS calls and accounts. A wrong migration
crashes the app on update and a destructive one deletes field work. Both are unacceptable.

## Steps

1. **Entity**: add the `@Entity` (or the field). For a column added to an existing table,
   give it `@ColumnInfo(defaultValue = "...")` - `""` for TEXT, `"0"` for INTEGER/Boolean -
   so the exported schema has the same `DEFAULT` as the `ALTER TABLE`.
2. **DAO**: add it, and `abstract fun xDao()` in `AppDatabase`.
3. **Version**: bump `version = N` in `AppDatabase` and add the entity to `entities`.
4. **Migration**: write `MIGRATION_{N-1}_N` next to the others and add it to
   `.addMigrations(...)`. Only `CREATE TABLE IF NOT EXISTS`, `CREATE [UNIQUE] INDEX IF NOT EXISTS`
   and `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT ...`. Never drop or rewrite user tables.
   Write the KDoc: what it adds, and that nothing stored is touched.
5. **Build** (`./gradlew.bat assembleDebug`) - KSP exports `app/schemas/<db>/N.json`.
6. **Compare** the migration with the export, character for character:

   ```bash
   python -I -c "
   import json,glob
   d=json.load(open(sorted(glob.glob('app/schemas/*/*.json'), key=lambda p:int(p.rsplit('/',1)[-1].split('.')[0]))[-1]))
   for e in d['database']['entities']:
       print(e['tableName'], '::', e['createSql'])
       for i in e.get('indices', []): print('   IDX', i['createSql'])
   "
   ```

   Column order, types, `NOT NULL`, `DEFAULT`, `PRIMARY KEY ... AUTOINCREMENT` and index
   names must all match (`${TABLE_NAME}` stands for the table name).
7. **Commit** the new `N.json` with the code. Then install over the previous version on the
   phone and launch it: that is the real migration test (see the `verify-and-ship` skill).
8. Update `CLAUDE.md` (the AppDatabase row: version and table list).

## This project's history

1→2 walk_path · 2→3 gps_point · … · 7→8 track_log.encodedVertexInfo · 8→9 sos_report ·
9→10 user_account, tourist_position, sos_report.userId/touristId/isDemo/synced.
