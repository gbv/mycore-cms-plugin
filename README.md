# mycore-cms-plugin

Ein Content-Management-System (CMS) Plugin für MyCoRe-Anwendungen. Ermöglicht die Verwaltung von Seiten mit Versionierung, mehrsprachigen Übersetzungen und einem flexiblen Berechtigungssystem.

## Features

- **Seitenverwaltung** mit URL-Slugs
- **Versionierung** - Alle Änderungen werden als unveränderliche Versionen gespeichert
- **Mehrsprachigkeit** - Jede Version kann Übersetzungen in beliebigen Sprachen enthalten
- **Workflow-Status** - Draft, Published, Archived
- **Asset-Verwaltung** - Dateien und Verzeichnisse für CMS-Inhalte
- **Feingranulare Berechtigungen** - Integration mit dem MyCoRe Permission-System

---

## Schema

### Page
| Feld       | Typ         | Beschreibung                      |
|------------|-------------|-----------------------------------|
| id         | primary_key |                                   |
| slug       | string      | URL-Pfad der Seite, z.B. "/about" |
| created_at | datetime    |                                   |
| updated_at | datetime    |                                   |

### Languages
| Feld | Typ         | Beschreibung                  |
|------|-------------|-------------------------------|
| id   | primary_key |                               |
| name | string      | z.B. "Deutsch"                |
| code | string      | ISO 639-1 Code, z.B. "de"     |

### Page_Versions
| Feld           | Typ         | Beschreibung                              |
|----------------|-------------|-------------------------------------------|
| id             | primary_key |                                           |
| page_id        | foreign_key | → Page.id                                 |
| version_number | integer     | Versionsnummer dieser Seite               |
| created_at     | datetime    | Erstellungszeitpunkt der Version          |
| created_by     | string      | Nutzer-ID des Erstellers                  |
| comment        | string?     | Optional: z.B. "Tippfehler korrigiert"    |
| status         | enum        | `draft`, `published`, `archived`          |

### Page_Version_Translations
| Feld            | Typ         | Beschreibung                     |
|-----------------|-------------|----------------------------------|
| id              | primary_key |                                  |
| page_version_id | foreign_key | → Page_Versions.id               |
| language_id     | foreign_key | → Languages.id                   |
| title           | string      | Titel in der übersetzten Sprache |
| content         | text        | Inhalt in der übersetzten Sprache|

---

## API

Base URL: `/api/cms/v1`

> **Hinweis:** Die API bildet nicht 1:1 das Datenbankschema ab, sondern kombiniert 
> `Page_Versions` und `Page_Version_Translations` zu einer einheitlichen Ressource.
> Dies vereinfacht die Nutzung und entkoppelt die API vom internen Schema.

### Endpoints

#### Pages

| Methode | Endpoint                                       | Beschreibung                              |
|---------|------------------------------------------------|-------------------------------------------|
| GET     | `/pages`                                       | Alle Seiten auflisten                     |
| GET     | `/pages?slug={slug}`                           | Seite nach Slug suchen                    |
| GET     | `/pages/{pageId}`                              | Eine Seite mit allen Versionen            |
| POST    | `/pages`                                       | Neue Seite erstellen                      |
| DELETE  | `/pages/{pageId}`                              | Seite löschen (auf archived setzen)       |
| GET     | `/pages/{pageId}/versions`                     | Alle Versionen einer Seite                |
| GET     | `/pages/{pageId}/versions/current`             | Höchste Version (für Editor)              |
| GET     | `/pages/{pageId}/versions/published`           | Höchste published Version (für Anzeige)   |
| GET     | `/pages/{pageId}/versions/{versionNumber}`     | Eine bestimmte Version mit Translations   |
| POST    | `/pages/{pageId}/versions`                     | Neue Version erstellen                    |
| GET     | `/pages/{pageId}/versions/{versionNumber}/{lang}` | Eine Übersetzung einer Version         |

#### Assets

| Methode | Endpoint                     | Beschreibung                                      |
|---------|------------------------------|---------------------------------------------------|
| GET     | `/assets`                    | Assets im Wurzelverzeichnis auflisten             |
| GET     | `/assets?path={path}`        | Assets in einem Unterverzeichnis auflisten        |
| GET     | `/assets/_config`            | Upload-Konfiguration (max. Größe) abrufen         |
| GET     | `/assets/{path}`             | Datei herunterladen oder Verzeichnis auflisten    |
| GET     | `/assets/{path}?info=true`   | Metadaten einer Datei/eines Ordners abrufen       |
| POST    | `/assets/{path}`             | Datei hochladen                                   |
| POST    | `/assets/{path}?directory=true` | Verzeichnis erstellen                          |
| PUT     | `/assets/{path}`             | Datei/Ordner verschieben oder umbenennen          |
| DELETE  | `/assets/{path}`             | Datei oder leeres Verzeichnis löschen             |
| DELETE  | `/assets/{path}?recursive=true` | Verzeichnis rekursiv löschen                   |

### Response Modelle

#### GET `/pages`
```json
[
  {
    "id": 1,
    "slug": "/about",
    "created_at": "2026-01-08T10:00:00Z",
    "updated_at": "2026-01-08T12:00:00Z",
    "current_version": {
      "version_number": 3,
      "status": "published",
      "created_at": "2026-01-08T12:00:00Z"
    }
  }
]
```

#### GET `/pages/{pageId}`
```json
{
  "id": 1,
  "slug": "/about",
  "created_at": "2026-01-08T10:00:00Z",
  "updated_at": "2026-01-08T12:00:00Z",
  "versions": [
    {
      "version_number": 3,
      "status": "published",
      "comment": "Tippfehler korrigiert",
      "created_at": "2026-01-08T12:00:00Z",
      "created_by": "admin"
    },
    {
      "version_number": 2,
      "status": "published",
      "created_at": "2026-01-07T10:00:00Z",
      "created_by": "editor1"
    }
  ]
}
```

#### GET `/pages/{pageId}/versions`
```json
[
  {
    "version_number": 3,
    "status": "published",
    "comment": "Tippfehler korrigiert",
    "created_at": "2026-01-08T12:00:00Z",
    "created_by": "admin"
  },
  {
    "version_number": 2,
    "status": "published",
    "created_at": "2026-01-07T10:00:00Z",
    "created_by": "editor1"
  }
]
```

#### GET `/pages/{pageId}/versions/{versionNumber}`
```json
{
  "version_number": 3,
  "status": "published",
  "comment": "Tippfehler korrigiert",
  "created_at": "2026-01-08T12:00:00Z",
  "created_by": "admin",
  "translations": [
    {
      "language": "de",
      "title": "Über uns",
      "content": "Willkommen..."
    },
    {
      "language": "en",
      "title": "About us",
      "content": "Welcome..."
    }
  ]
}
```

#### GET `/pages/{pageId}/versions/{versionNumber}/{lang}`
```json
{
  "version_number": 3,
  "status": "published",
  "language": "de",
  "title": "Über uns",
  "content": "Willkommen..."
}
```

#### POST `/pages/{pageId}/versions`
Erstellt eine neue Version mit Übersetzungen:
```json
{
  "status": "draft",
  "comment": "Neue Ankündigung",
  "translations": [
    {
      "language": "de",
      "title": "Über uns",
      "content": "Neuer Inhalt..."
    }
  ]
}
```

#### GET `/assets` oder `/assets/{path}` (Verzeichnis)
```json
[
  {
    "name": "images",
    "path": "images",
    "directory": true,
    "modified_at": "2026-01-08T10:00:00Z"
  },
  {
    "name": "logo.png",
    "path": "logo.png",
    "directory": false,
    "size": 12345,
    "content_type": "image/png",
    "modified_at": "2026-01-08T12:00:00Z"
  }
]
```

#### GET `/assets/{path}?info=true`
```json
{
  "name": "logo.png",
  "path": "images/logo.png",
  "directory": false,
  "size": 12345,
  "content_type": "image/png",
  "modified_at": "2026-01-08T12:00:00Z"
}
```

#### GET `/assets/_config`
```json
{
  "max_upload_size": 10485760
}
```

#### PUT `/assets/{path}` (Verschieben/Umbenennen)
Request:
```json
{
  "target": "new/path/to/file.png"
}
```

---

## Berechtigungen

Die Rechteprüfung erfolgt über das bestehende MyCoRe Permission-System:

```java
public static boolean checkPermission(String id, String permission)
```

### Permission-ID Format

#### Pages
```
cms:page:{slug}
```

Beispiel: `cms:page:/about`, `cms:page:/news/2026`

### Permissions

#### Pages

| Permission       | Beschreibung                                              |
|------------------|-----------------------------------------------------------|
| `read`           | Grundrecht zum Lesen einer Seite                          |
| `write`          | Kann neue Versionen erstellen                             |
| `delete`         | Kann die Seite auf `archived` setzen                      |
| `read-draft`     | Kann Draft-Versionen lesen                                |
| `read-archived`  | Kann archivierte Versionen und archivierte Seiten lesen   |
| `read-versions`  | Kann alle Versionen der Seite sehen                       |

### Sichtbarkeit von Seiten

Die Sichtbarkeit einer Seite hängt von der **letzten non-draft Version** ab:

| Letzte non-draft Version | Benötigte Permission für Seitenzugriff |
|--------------------------|----------------------------------------|
| `published`              | `read`                                 |
| `archived`               | `read` + `read-archived`               |
| Keine (nur Drafts)       | `read` + `read-draft`                  |

### Sichtbarkeit von Versionen

Zusätzlich zur Seitensichtbarkeit wird pro Version geprüft:

| Versions-Status | Benötigte Permission |
|-----------------|----------------------|
| `published`     | `read`               |
| `draft`         | `read-draft`         |
| `archived`      | `read-archived`      |

#### Assets

Permission-ID Format: `cms:asset:{path}`

| Permission           | Beschreibung                                  |
|----------------------|-----------------------------------------------|
| `read`               | Kann Assets auflisten und herunterladen       |
| `write`              | Kann Assets hochladen, erstellen, verschieben |
| `delete`             | Kann Assets löschen                           |

### API-Endpoint Berechtigungen

#### Pages

> **Hinweis:** Bei allen Seiten-Endpoints wird zuerst die Seitensichtbarkeit geprüft 
> (basierend auf der letzten non-draft Version), dann die Berechtigung für einzelne Versionen.

| Endpoint                                    | Benötigte Permission                          | Verhalten         |
|---------------------------------------------|-----------------------------------------------|-------------------|
| `GET /pages`                                | Seitensichtbarkeit                            | Liste filtern     |
| `GET /pages/{id}`                           | Seitensichtbarkeit                            | 403 wenn verweigert |
| `GET /pages/{id}/versions`                  | Seitensichtbarkeit + `read-versions`          | 403 wenn verweigert, Versionen gefiltert |
| `GET /pages/{id}/versions/{v}`              | Seitensichtbarkeit + Versionsberechtigung     | 403 wenn verweigert |
| `GET /pages/{id}/versions/current`          | Seitensichtbarkeit + Versionsberechtigung     | 403 wenn verweigert |
| `GET /pages/{id}/versions/published`        | Seitensichtbarkeit                            | 404 wenn keine published |
| `POST /pages/{id}/versions`                 | `write`                                       | 403 wenn verweigert |
| `DELETE /pages/{id}`                        | `delete`                                      | 403 wenn verweigert |

#### Assets

| Endpoint                                    | Benötigte Permission      |
|---------------------------------------------|---------------------------|
| `GET /assets`                               | `read`                    |
| `GET /assets/{path}`                        | `read`                    |
| `GET /assets/_config`                       | `read`                    |
| `POST /assets/{path}`                       | `write`                   |
| `PUT /assets/{path}`                        | `write`                   |
| `DELETE /assets/{path}`                     | `delete`                  |

---

## Versionierung

**Versionen sind nach dem Erstellen unveränderlich (immutable)!**

### Status-Werte

| Status      | Bedeutung                        |
|-------------|----------------------------------|
| `draft`     | Entwurf, noch nie veröffentlicht |
| `published` | Veröffentlichte Version          |
| `archived`  | Seite wurde offline genommen     |

### Anzeige
- Die Sichtbarkeit hängt von der letzten non-draft Version ab:
  - Wenn `published` → Seite wird angezeigt (mit `read` Recht)
  - Wenn `archived` → Seite nur mit `read-archived` sichtbar, sonst **404**
  - Wenn nur Drafts existieren → Seite nur mit `read-draft` sichtbar, sonst **404**
- Draft-Versionen werden nur mit `read-draft` Recht angezeigt
- Archived-Versionen werden nur mit `read-archived` Recht angezeigt

### Editor
- Höchste Version (egal welcher Status) wird geladen
- Nutzer kann auch ältere Versionen auswählen

### Aktionen

| Aktion          | Ergebnis                                        |
|-----------------|-------------------------------------------------|
| Veröffentlichen | Neue Version erstellen mit `status='published'` |
| Offline nehmen  | Neue Version erstellen mit `status='archived'`  |
