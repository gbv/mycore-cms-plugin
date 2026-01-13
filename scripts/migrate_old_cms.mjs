#!/usr/bin/env node

/**
 * CMS Migration Script
 *
 * Migriert Daten aus dem alten CMS (Directus-Format) in das neue MyCoRe CMS.
 *
 * Verwendung:
 *   node migrate_old_cms.mjs [optionen]
 *
 * Optionen:
 *   -h, --help          Diese Hilfe anzeigen
 *   -u, --user          API-Benutzer (Standard: administrator)
 *   -p, --password      API-Passwort (Standard: alleswirdgut)
 *   -b, --base-url      Basis-URL der API (Standard: http://localhost:8291/mir)
 *   -d, --data-dir      Verzeichnis mit den alten Daten (Standard: ./old)
 *   --dry-run           Nur simulieren, keine Änderungen vornehmen
 *   --verbose           Ausführliche Ausgabe
 *   --skip-assets       Assets nicht migrieren
 */

import { readFileSync, writeFileSync, existsSync, readdirSync } from 'fs';
import { join, dirname, extname } from 'path';
import { fileURLToPath } from 'url';
import { parseArgs } from 'util';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// ANSI Farben
const Colors = {
  RED: '\x1b[31m',
  GREEN: '\x1b[32m',
  YELLOW: '\x1b[33m',
  BLUE: '\x1b[34m',
  CYAN: '\x1b[36m',
  NC: '\x1b[0m',
};

// Logging-Funktionen
const log = {
  info: (msg) => console.log(`${Colors.BLUE}[INFO]${Colors.NC} ${msg}`),
  success: (msg) => console.log(`${Colors.GREEN}[OK]${Colors.NC} ${msg}`),
  warn: (msg) => console.log(`${Colors.YELLOW}[WARN]${Colors.NC} ${msg}`),
  error: (msg) => console.log(`${Colors.RED}[ERROR]${Colors.NC} ${msg}`),
  debug: (msg, verbose) => verbose && console.log(`${Colors.CYAN}[DEBUG]${Colors.NC} ${msg}`),
};

// Sprachcode-Mapping
const LANGUAGE_MAP = {
  'de-DE': 'de',
  'en-US': 'en',
  'fr-FR': 'fr',
};

/**
 * Konvertiert alte Sprachcodes (de-DE) zu neuen (de)
 */
function convertLanguageCode(oldCode) {
  if (!oldCode) return null;
  return LANGUAGE_MAP[oldCode] || oldCode.split('-')[0];
}

/**
 * Extrahiert einen Titel aus dem HTML-Content
 */
function extractTitleFromContent(content, fallback = '') {
  if (!content) return fallback;

  // Suche nach h1, h2, h3 Tags
  const patterns = [
    /<h1[^>]*>([^<]+)<\/h1>/i,
    /<h2[^>]*>([^<]+)<\/h2>/i,
    /<h3[^>]*>([^<]+)<\/h3>/i,
  ];

  for (const pattern of patterns) {
    const match = content.match(pattern);
    if (match) {
      let title = match[1].trim();
      // HTML-Entities dekodieren
      title = decodeHtmlEntities(title);
      // HTML-Tags entfernen
      title = title.replace(/<[^>]+>/g, '');
      return title;
    }
  }

  return fallback;
}

/**
 * Dekodiert HTML-Entities
 */
function decodeHtmlEntities(str) {
  const entities = {
    '&nbsp;': ' ',
    '&amp;': '&',
    '&lt;': '<',
    '&gt;': '>',
    '&quot;': '"',
    '&apos;': "'",
    '&ndash;': '–',
    '&mdash;': '—',
    '&laquo;': '«',
    '&raquo;': '»',
    '&ldquo;': '"',
    '&rdquo;': '"',
    '&lsquo;': '\'',
    '&rsquo;': '\'',
    '&szlig;': 'ß',
    '&auml;': 'ä',
    '&ouml;': 'ö',
    '&uuml;': 'ü',
    '&Auml;': 'Ä',
    '&Ouml;': 'Ö',
    '&Uuml;': 'Ü',
    '&eacute;': 'é',
    '&egrave;': 'è',
    '&agrave;': 'à',
    '&ccedil;': 'ç',
  };

  let result = str;
  for (const [entity, char] of Object.entries(entities)) {
    result = result.replaceAll(entity, char);
  }
  // Numerische Entities
  result = result.replace(/&#(\d+);/g, (_, num) => String.fromCharCode(parseInt(num)));
  return result;
}

/**
 * Lädt eine JSON-Datei
 */
function loadJsonFile(dataDir, filename) {
  const filepath = join(dataDir, filename);

  if (!existsSync(filepath)) {
    log.error(`Datei nicht gefunden: ${filepath}`);
    return null;
  }

  try {
    const content = readFileSync(filepath, 'utf-8');
    return JSON.parse(content);
  } catch (e) {
    log.error(`JSON-Fehler in ${filepath}: ${e.message}`);
    return null;
  }
}

/**
 * UUID-Regex Pattern für Asset-URLs
 */
const UUID_PATTERN = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;

/**
 * Pattern für Asset-URLs im Content
 * Matches: ../../cms/assets/UUID, /cms/assets/UUID, cms/assets/UUID etc.
 */
const ASSET_URL_PATTERN = /(?:\.\.\/)*(?:\/)?cms\/assets\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:\?[^"'\s]*)*/gi;

/**
 * Baut einen Index der Dateien im files-Verzeichnis auf
 * @returns Map<UUID, {filename, extension, fullPath}>
 */
function buildFileIndex(filesDir) {
  const fileIndex = new Map();

  if (!existsSync(filesDir)) {
    log.warn(`Files-Verzeichnis nicht gefunden: ${filesDir}`);
    return fileIndex;
  }

  const files = readdirSync(filesDir);

  for (const filename of files) {
    // Extrahiere UUID aus Dateiname (Format: UUID_splitme_restname.ext)
    const uuidMatch = filename.match(/^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i);

    if (uuidMatch) {
      const uuid = uuidMatch[1].toLowerCase();
      const extension = extname(filename);
      const fullPath = join(filesDir, filename);

      fileIndex.set(uuid, {
        filename,
        extension,
        fullPath,
      });

      log.debug(`Indexiert: ${uuid} -> ${filename}`, false);
    }
  }

  log.info(`${fileIndex.size} Dateien im Index`);
  return fileIndex;
}

/**
 * Extrahiert alle Asset-UUIDs aus einem HTML-Content
 */
function extractAssetUuids(content) {
  const uuids = new Set();

  if (!content) return uuids;

  let match;
  while ((match = ASSET_URL_PATTERN.exec(content)) !== null) {
    uuids.add(match[1].toLowerCase());
  }

  // Reset regex lastIndex
  ASSET_URL_PATTERN.lastIndex = 0;

  return uuids;
}

/**
 * Ersetzt Asset-URLs im Content mit dem neuen Format
 * @param content Der HTML-Content
 * @param fileIndex Der Datei-Index
 * @param assetPrefix Prefix für die neuen Asset-URLs (z.B. "qed")
 * @returns Der aktualisierte Content
 */
function replaceAssetUrls(content, fileIndex, assetPrefix = 'qed') {
  if (!content) return content;

  return content.replace(ASSET_URL_PATTERN, (match, uuid) => {
    const lowerUuid = uuid.toLowerCase();
    const fileInfo = fileIndex.get(lowerUuid);

    if (fileInfo) {
      // Neues URL-Format: $assets$/prefix/uuid.extension
      const newUrl = `$assets$/${assetPrefix}/${lowerUuid}${fileInfo.extension}`;
      log.debug(`URL ersetzt: ${match} -> ${newUrl}`, false);
      return newUrl;
    }

    // Datei nicht gefunden, URL beibehalten aber warnen
    log.warn(`Datei für UUID nicht gefunden: ${uuid}`);
    return match;
  });
}

/**
 * CMS Migrator Klasse
 */
class CMSMigrator {
  constructor(config) {
    this.config = config;
    this.token = null;
    this.idMapping = {};
    this.fileIndex = new Map();
    this.uploadedAssets = new Set();
  }

  get cmsApiUrl() {
    return `${this.config.baseUrl}/api/cms/v1`;
  }

  get authUrl() {
    return `${this.config.baseUrl}/api/v2/auth/login`;
  }

  /**
   * Authentifiziert sich bei der API
   */
  async authenticate() {
    log.info(`Authentifiziere bei ${this.authUrl}...`);

    try {
      // Basic Auth für Login
      const credentials = Buffer.from(`${this.config.user}:${this.config.password}`).toString('base64');

      const response = await fetch(this.authUrl, {
        method: 'GET',
        headers: {
          Authorization: `Basic ${credentials}`,
        },
      });

      if (!response.ok) {
        log.error(`Authentifizierung fehlgeschlagen: HTTP ${response.status}`);
        log.error(await response.text());
        return false;
      }

      // Token aus Header extrahieren
      this.token = response.headers.get('Authorization')?.replace('Bearer ', '');

      if (!this.token) {
        // Versuche aus JSON-Body
        try {
          const data = await response.json();
          this.token = data.access_token || '';
        } catch {
          // Ignore JSON parse error
        }
      }

      if (!this.token) {
        log.error('Kein Token erhalten!');
        return false;
      }

      log.success('Authentifizierung erfolgreich!');
      return true;
    } catch (e) {
      log.error(`Verbindungsfehler: ${e.message}`);
      return false;
    }
  }

  /**
   * Führt einen API-Aufruf durch
   */
  async apiCall(method, endpoint, data = null) {
    const url = `${this.cmsApiUrl}${endpoint}`;

    if (this.config.dryRun && method.toUpperCase() !== 'GET') {
      log.warn(`[DRY-RUN] Würde ausführen: ${method} ${url}`);
      if (data) {
        log.debug(JSON.stringify(data, null, 2), this.config.verbose);
      }
      return { success: true, data: { id: 0, version_number: 1 } };
    }

    try {
      const options = {
        method: method.toUpperCase(),
        headers: {
          Authorization: `Bearer ${this.token}`,
          'Content-Type': 'application/json',
        },
      };

      if (data && method.toUpperCase() !== 'GET') {
        options.body = JSON.stringify(data);
      }

      const response = await fetch(url, options);

      log.debug(`API Response: ${response.status}`, this.config.verbose);

      if (response.ok) {
        try {
          const responseData = await response.json();
          return { success: true, data: responseData };
        } catch {
          return { success: true, data: {} };
        }
      } else {
        log.error(`API-Fehler: HTTP ${response.status}`);
        log.error(await response.text());
        return { success: false, data: {} };
      }
    } catch (e) {
      log.error(`API-Aufruf fehlgeschlagen: ${e.message}`);
      return { success: false, data: {} };
    }
  }

  /**
   * Erstellt eine neue Seite
   */
  async createPage(slug) {
    log.info(`Erstelle Seite: ${slug}`);

    const { success, data } = await this.apiCall('POST', '/pages', { slug });

    if (!success) {
      return null;
    }

    let pageId = data.id;

    // Falls die API keine ID zurückgibt (Bug in älteren Versionen),
    // versuche die Seite per Slug abzurufen
    if (pageId === undefined || pageId === null) {
      log.warn('Keine ID in Response, versuche Lookup per Slug...');
      const lookupResult = await this.apiCall('GET', `/pages?slug=${encodeURIComponent(slug)}`);
      if (lookupResult.success && Array.isArray(lookupResult.data) && lookupResult.data.length > 0) {
        pageId = lookupResult.data[0].id;
      }
    }

    if (pageId !== undefined && pageId !== null) {
      log.success(`Seite erstellt mit ID: ${pageId}`);
      return pageId;
    }

    log.error(`Keine Page-ID erhalten für: ${slug}`);
    return null;
  }

  /**
   * Erstellt eine neue Version mit Übersetzungen
   */
  async createVersion(pageId, status, translations) {
    log.info(`Erstelle Version für Seite ${pageId} (Status: ${status})`);

    const payload = {
      status,
      comment: 'Import aus altem CMS',
      translations,
    };

    const { success, data } = await this.apiCall('POST', `/pages/${pageId}/versions`, payload);

    if (success) {
      const versionNumber = data.version_number || '?';
      log.success(`Version ${versionNumber} erstellt`);
      return true;
    }

    return false;
  }

  /**
   * Lädt ein Asset hoch
   * @param uuid Die UUID des Assets
   * @param assetPrefix Prefix für den Pfad (z.B. "qed")
   * @returns true bei Erfolg
   */
  async uploadAsset(uuid, assetPrefix = 'qed') {
    const fileInfo = this.fileIndex.get(uuid);

    if (!fileInfo) {
      log.error(`Keine Datei gefunden für UUID: ${uuid}`);
      return false;
    }

    const targetPath = `${assetPrefix}/${uuid}${fileInfo.extension}`;

    // Bereits hochgeladen?
    if (this.uploadedAssets.has(uuid)) {
      log.debug(`Asset bereits hochgeladen: ${targetPath}`, this.config.verbose);
      return true;
    }

    log.info(`Lade Asset hoch: ${fileInfo.filename} -> ${targetPath}`);

    if (this.config.dryRun) {
      log.warn(`[DRY-RUN] Würde hochladen: ${targetPath}`);
      this.uploadedAssets.add(uuid);
      return true;
    }

    try {
      // Datei lesen
      const fileContent = readFileSync(fileInfo.fullPath);

      const url = `${this.cmsApiUrl}/assets/${targetPath}`;

      const response = await fetch(url, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${this.token}`,
          'Content-Length': fileContent.length.toString(),
        },
        body: fileContent,
      });

      if (response.ok) {
        log.success(`Asset hochgeladen: ${targetPath}`);
        this.uploadedAssets.add(uuid);
        return true;
      } else if (response.status === 409) {
        // Conflict - Datei existiert bereits
        log.warn(`Asset existiert bereits: ${targetPath}`);
        this.uploadedAssets.add(uuid);
        return true;
      } else {
        log.error(`Asset-Upload fehlgeschlagen: HTTP ${response.status}`);
        log.error(await response.text());
        return false;
      }
    } catch (e) {
      log.error(`Asset-Upload Fehler: ${e.message}`);
      return false;
    }
  }

  /**
   * Erstellt das Verzeichnis für Assets
   */
  async createAssetDirectory(dirPath) {
    log.info(`Erstelle Asset-Verzeichnis: ${dirPath}`);

    if (this.config.dryRun) {
      log.warn(`[DRY-RUN] Würde Verzeichnis erstellen: ${dirPath}`);
      return true;
    }

    try {
      const url = `${this.cmsApiUrl}/assets/${dirPath}?directory=true`;

      const response = await fetch(url, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${this.token}`,
        },
      });

      if (response.ok) {
        log.success(`Verzeichnis erstellt: ${dirPath}`);
        return true;
      } else if (response.status === 409) {
        // Conflict - Verzeichnis existiert bereits
        log.info(`Verzeichnis existiert bereits: ${dirPath}`);
        return true;
      } else {
        log.error(`Verzeichnis-Erstellung fehlgeschlagen: HTTP ${response.status}`);
        return false;
      }
    } catch (e) {
      log.error(`Verzeichnis-Erstellung Fehler: ${e.message}`);
      return false;
    }
  }

  /**
   * Extrahiert und lädt alle Assets aus den Übersetzungen hoch
   * @param translations Array von Übersetzungen
   * @param assetPrefix Prefix für Asset-Pfade
   * @returns Set der hochgeladenen UUIDs
   */
  async uploadAssetsFromTranslations(translations, assetPrefix = 'qed') {
    const allUuids = new Set();

    // Sammle alle UUIDs aus allen Übersetzungen
    for (const trans of translations) {
      const uuids = extractAssetUuids(trans.content);
      for (const uuid of uuids) {
        allUuids.add(uuid);
      }
    }

    if (allUuids.size === 0) {
      return allUuids;
    }

    log.info(`Gefunden: ${allUuids.size} Assets in Übersetzungen`);

    // Lade alle Assets hoch
    for (const uuid of allUuids) {
      await this.uploadAsset(uuid, assetPrefix);
    }

    return allUuids;
  }

  /**
   * Führt die Migration durch
   */
  async migrate() {
    log.info('='.repeat(60));
    log.info('CMS Migration gestartet');
    log.info('='.repeat(60));
    log.info(`Daten-Verzeichnis: ${this.config.dataDir}`);
    log.info(`API-Endpoint: ${this.cmsApiUrl}`);

    if (this.config.dryRun) {
      log.warn('=== DRY-RUN MODUS - Keine Änderungen werden durchgeführt ===');
    }

    // Datei-Index aufbauen (für Asset-Migration)
    const filesDir = join(this.config.dataDir, 'files');
    if (!this.config.skipAssets) {
      log.info('Baue Datei-Index auf...');
      this.fileIndex = buildFileIndex(filesDir);
    }

    // Daten laden
    const pages = loadJsonFile(this.config.dataDir, 'Page.json');
    const translations = loadJsonFile(this.config.dataDir, 'Page_translations.json');
    const languages = loadJsonFile(this.config.dataDir, 'languages.json');

    if (!pages) {
      log.error('Keine Seiten gefunden!');
      return false;
    }

    log.info(`Gefunden: ${pages.length} Seiten`);
    log.info(`Gefunden: ${translations?.length || 0} Übersetzungen`);
    log.info(`Gefunden: ${languages?.length || 0} Sprachen`);

    // Übersetzungen nach Page_id indexieren
    const translationsByPage = new Map();
    for (const trans of translations || []) {
      const pageId = trans.Page_id;
      if (pageId !== undefined) {
        if (!translationsByPage.has(pageId)) {
          translationsByPage.set(pageId, []);
        }
        translationsByPage.get(pageId).push(trans);
      }
    }

    // Authentifizierung
    if (!await this.authenticate()) {
      return false;
    }

    // Asset-Verzeichnis erstellen (falls Assets migriert werden)
    const assetPrefix = 'qed';
    if (!this.config.skipAssets && this.fileIndex.size > 0) {
      await this.createAssetDirectory(assetPrefix);
    }

    // Statistik
    let successCount = 0;
    let errorCount = 0;
    let assetCount = 0;

    // Seiten durchlaufen
    for (const page of pages) {
      const oldId = page.id;
      const slug = '/qed' + page.slug || '';
      const status = page.status || 'draft';
      const project = page.project || 'default';

      log.info('-'.repeat(50));
      log.info(`Verarbeite Seite ${oldId}: ${slug} (Projekt: ${project})`);

      // Seite erstellen
      const newPageId = await this.createPage(slug);

      if (newPageId === null) {
        log.error(`Überspringe Seite ${oldId}`);
        errorCount++;
        continue;
      }

      // Mapping speichern
      this.idMapping[String(oldId)] = newPageId;

      // Übersetzungen für diese Seite sammeln
      const pageTranslations = translationsByPage.get(oldId) || [];
      log.info(`Gefunden: ${pageTranslations.length} Übersetzungen`);

      if (pageTranslations.length === 0) {
        log.warn(`Keine Übersetzungen für Seite ${oldId} gefunden`);
        successCount++;
        continue;
      }

      // Übersetzungen in das neue Format konvertieren
      const newTranslations = [];

      for (const trans of pageTranslations) {
        const langCode = trans.languages_code;
        let content = trans.content || '';

        if (!langCode) {
          log.warn('Überspringe Übersetzung ohne Sprachcode');
          continue;
        }

        // Sprachcode konvertieren
        const newLangCode = convertLanguageCode(langCode);

        // Assets hochladen und URLs ersetzen (falls nicht übersprungen)
        if (!this.config.skipAssets && this.fileIndex.size > 0) {
          const assetUuids = extractAssetUuids(content);
          if (assetUuids.size > 0) {
            log.info(`  Gefunden: ${assetUuids.size} Assets in ${newLangCode}-Übersetzung`);

            // Assets hochladen
            for (const uuid of assetUuids) {
              if (await this.uploadAsset(uuid, assetPrefix)) {
                assetCount++;
              }
            }

            // URLs im Content ersetzen
            content = replaceAssetUrls(content, this.fileIndex, assetPrefix);
          }
        }

        // Titel extrahieren
        const title = extractTitleFromContent(content, `Seite ${oldId}`);

        log.info(`  - Sprache: ${newLangCode}, Titel: ${title.substring(0, 50)}...`);

        newTranslations.push({
          language: newLangCode,
          title,
          content,
        });
      }

      // Status konvertieren
      const statusMap = {
        published: 'published',
        draft: 'draft',
        archived: 'archived',
      };
      const newStatus = statusMap[status] || 'draft';

      // Version erstellen mit allen Übersetzungen
      if (newTranslations.length > 0) {
        if (await this.createVersion(newPageId, newStatus, newTranslations)) {
          successCount++;
        } else {
          errorCount++;
        }
      } else {
        log.warn('Keine gültigen Übersetzungen für Version');
        successCount++;
      }
    }

    // Zusammenfassung
    log.info('='.repeat(60));
    log.info('Migration abgeschlossen');
    log.info('='.repeat(60));
    log.success(`Erfolgreich: ${successCount} Seiten`);
    if (!this.config.skipAssets) {
      log.success(`Hochgeladen: ${this.uploadedAssets.size} Assets`);
    }
    if (errorCount > 0) {
      log.error(`Fehler: ${errorCount} Seiten`);
    }

    // ID-Mapping ausgeben
    log.info('\nPage-ID Mapping (alt -> neu):');
    console.log(JSON.stringify(this.idMapping, null, 2));

    // Mapping in Datei speichern
    const mappingFile = join(this.config.dataDir, 'id_mapping.json');
    writeFileSync(mappingFile, JSON.stringify(this.idMapping, null, 2));
    log.info(`ID-Mapping gespeichert in: ${mappingFile}`);

    return errorCount === 0;
  }
}

/**
 * Hilfe anzeigen
 */
function showHelp() {
  console.log(`
CMS Migration Script

Migriert Daten aus dem alten CMS (Directus-Format) in das neue MyCoRe CMS.
Migriert auch Assets (Bilder, Dateien) und passt URLs im Content an.

Verwendung:
  node migrate_old_cms.mjs [optionen]

Optionen:
  -h, --help          Diese Hilfe anzeigen
  -u, --user          API-Benutzer (Standard: administrator)
  -p, --password      API-Passwort (Standard: alleswirdgut)
  -b, --base-url      Basis-URL der API (Standard: http://localhost:8291/mir)
  -d, --data-dir      Verzeichnis mit den alten Daten (Standard: ./old)
  --dry-run           Nur simulieren, keine Änderungen vornehmen
  --verbose           Ausführliche Ausgabe
  --skip-assets       Assets nicht migrieren

Dateistruktur:
  Das data-dir sollte folgende Struktur haben:
  ./old/
    Page.json              - Seiten-Daten
    Page_translations.json - Übersetzungen
    languages.json         - Sprachen
    files/                 - Asset-Dateien (UUID_splitme_name.ext)

Asset-Migration:
  - Dateien in ./old/files/ werden nach /assets/qed/ hochgeladen
  - URLs im Content werden von ../../cms/assets/UUID?...
    zu \$assets\$/qed/UUID.ext konvertiert

Beispiele:
  node migrate_old_cms.mjs --dry-run
  node migrate_old_cms.mjs -u admin -p secret -b http://localhost:8080/app
  node migrate_old_cms.mjs -d /pfad/zu/daten --verbose
  node migrate_old_cms.mjs --skip-assets
`);
}

/**
 * Main-Funktion
 */
async function main() {
  // Argumente parsen
  const { values } = parseArgs({
    options: {
      help: { type: 'boolean', short: 'h', default: false },
      user: { type: 'string', short: 'u', default: 'administrator' },
      password: { type: 'string', short: 'p', default: 'alleswirdgut' },
      'base-url': { type: 'string', short: 'b', default: 'http://localhost:8291/mir' },
      'data-dir': { type: 'string', short: 'd', default: join(__dirname, '..', 'old') },
      'dry-run': { type: 'boolean', default: false },
      verbose: { type: 'boolean', default: false },
      'skip-assets': { type: 'boolean', default: false },
    },
    strict: true,
  });

  if (values.help) {
    showHelp();
    process.exit(0);
  }

  const config = {
    user: values.user,
    password: values.password,
    baseUrl: values['base-url'],
    dataDir: values['data-dir'],
    dryRun: values['dry-run'],
    verbose: values.verbose,
    skipAssets: values['skip-assets'],
  };

  const migrator = new CMSMigrator(config);
  const success = await migrator.migrate();

  process.exit(success ? 0 : 1);
}

main().catch((e) => {
  log.error(`Unerwarteter Fehler: ${e.message}`);
  console.error(e);
  process.exit(1);
});

