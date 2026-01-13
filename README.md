# mycore-cms-plugin

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
page:{slug}
```

Beispiel: `page:/about`, `page:/news/2026`

### Permissions

#### Pages

| Permission       | Beschreibung                                      |
|------------------|---------------------------------------------------|
| `read`           | Kann die Seite lesen (nur wenn `published`)       |
| `write`          | Kann neue Versionen erstellen                     |
| `delete`         | Kann die Seite auf `archived` setzen              |
| `read-draft`     | Kann die Seite auch als `draft` lesen             |
| `read-archived`  | Kann die Seite auch als `archived` lesen          |
| `read-versions`  | Kann alle Versionen der Seite sehen               |

#### Assets

| Permission           | Beschreibung                                  |
|----------------------|-----------------------------------------------|
| `cms-assets-read`    | Kann Assets auflisten und herunterladen       |
| `cms-assets-write`   | Kann Assets hochladen, erstellen, verschieben |
| `cms-assets-delete`  | Kann Assets löschen                           |

### API-Endpoint Berechtigungen

#### Pages

| Endpoint                                    | Benötigte Permission              | Filterung         |
|---------------------------------------------|-----------------------------------|-------------------|
| `GET /pages`                                | `read`                            | Liste filtern     |
| `GET /pages/{id}`                           | `read`                            | 403 wenn verweigert |
| `GET /pages/{id}/versions`                  | `read` + `read-versions`          | 403 wenn verweigert |
| `GET /pages/{id}/versions/{v}`              | `read` (+ `read-draft` wenn Draft, + `read-archived` wenn Archived)| 403 wenn verweigert |
| `GET /pages/{id}/versions/current`          | `read` (+ `read-draft` wenn Draft, + `read-archived` wenn Archived)| 403 wenn verweigert |
| `GET /pages/{id}/versions/published`        | `read`                            | 403 wenn verweigert |
| `POST /pages/{id}/versions`                 | `write`                           | 403 wenn verweigert |
| `DELETE /pages/{id}`                        | `delete`                          | 403 wenn verweigert |

#### Assets

| Endpoint                                    | Benötigte Permission      |
|---------------------------------------------|---------------------------|
| `GET /assets`                               | `cms-assets-read`         |
| `GET /assets/{path}`                        | `cms-assets-read`         |
| `GET /assets/_config`                       | `cms-assets-read`         |
| `POST /assets/{path}`                       | `cms-assets-write`        |
| `PUT /assets/{path}`                        | `cms-assets-write`        |
| `DELETE /assets/{path}`                     | `cms-assets-delete`       |

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
- Höchste Version mit `status='published'` wird angezeigt
- Falls keine `published` existiert oder höchste Version ist `archived` → **404**
- Wenn eingeloggt und "Vorschau" aktiviert → höchste `draft` Version anzeigen

### Editor
- Höchste Version (egal welcher Status) wird geladen
- Nutzer kann auch ältere Versionen auswählen

### Aktionen

| Aktion          | Ergebnis                                        |
|-----------------|-------------------------------------------------|
| Veröffentlichen | Neue Version erstellen mit `status='published'` |
| Offline nehmen  | Neue Version erstellen mit `status='archived'`  |
