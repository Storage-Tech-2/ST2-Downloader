# Archive Repository API

This document explains how the archive viewer reads data from the archive repository (`Storage-Tech-2/Archive` on the `main` branch by default) and how to structure new content. All data is plain JSON stored in a Git repo; the UI fetches it either from `raw.githubusercontent.com` (default) or the GitHub Contents API.

## Base access
- Default repo: owner `Storage-Tech-2`, repo `Archive`, branch `main` (see `src/siteConfig.ts`).
- Raw URL pattern: `https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}`.
- Contents API (fallback when `USE_RAW` is false): `https://api.github.com/repos/{owner}/{repo}/contents/{path}?ref={branch}` with `Accept: application/vnd.github.raw`.

## Repository layout
```
config.json
dictionary/
  config.json
  entries/{id}.json
{channel.path}/
  data.json
  {entry.path}/
    data.json
    comments.json   (optional)
    <asset files referenced by Image/Attachment.path>
```
- `channel.path` comes from `config.json`.
- `entry.path` comes from the channel’s `data.json`.
- Asset paths are stored relative to the entry folder and expanded to raw URLs in the UI.

## Root config (`config.json`)
`ArchiveConfig` (see `src/types/schema.ts`):
- `archiveChannels`: array of channels to load.
- `postSchema`: JSON Schema for validating post bodies (not enforced in the client).
- `postStyle`: record of style overrides for rendering record sections.

### Channel reference (`archiveChannels[*]`)
`ChannelRef` fields:
- `id`: snowflake ID for the Discord thread/category.
- `name`: slug used for filtering.
- `code`: short code shown in badges (e.g., `FL`).
- `category`: human-readable grouping.
- `path`: folder in the repo containing the channel data.
- `description`: blurb for the channel.
- `availableTags`: list of tag names that can appear in this channel.

## Channel data (`{channel.path}/data.json`)
`ChannelData` fields:
- All `ChannelRef` fields except `path`.
- `currentCodeId`: counter for allocating new `code` values.
- `entries`: array of `EntryRef` objects describing posts in the channel.

### Entry reference (`entries[*]`)
`EntryRef` fields:
- `id`: Discord snowflake ID of the original message/thread.
- `name`: title.
- `code`: short per-channel code (e.g., `FL-012`).
- `timestamp` (legacy), `archivedAt`, `updatedAt`: milliseconds since epoch.
- `path`: folder name for the entry inside the channel directory.
- `tags`: tag names visible at the card level.

## Entry content (`{channel.path}/{entry.path}/data.json`)
`ArchiveEntryData` fields:
- Identity: `id`, `name`, `code`.
- People: `authors` and `endorsers` (array of `Author` objects; Discord and generic types supported, with optional `dontDisplay` or profile `url`).
- Classification: `tags` (full objects with `id`/`name`), `records` (keyed form sections), `styles` (per-record `StyleInfo` overrides).
- Media: `images` and `attachments` arrays. Each item may provide a direct `url` or a relative `path` (joined with the channel/entry path); `contentType` is used for rendering. Attachments can carry `litematic`, `wdl`, or `youtube` metadata. `canDownload` gates download links.
- References: `references` for inline links (Discord messages, dictionary terms, archived posts, user/channel mentions). `author_references` can be used to link acknowledgements text.
- Discord: `post` with `forumId`, `threadId`, message IDs, and `threadURL` to the originating thread.
- Timestamps: `timestamp` (legacy), `archivedAt`, `updatedAt`.

## Comments (`{channel.path}/{entry.path}/comments.json`, optional)
Array of `ArchiveComment`:
- `id`: unique comment ID.
- `sender`: `Author`.
- `content`: Markdown text.
- `attachments`: same shape as post attachments; `path` is relative to the entry folder.
- `timestamp`: milliseconds since epoch.

## Dictionary data (same repo)
- `dictionary/config.json`: `DictionaryConfig` with `entries` array of index objects (`id`, `terms[]`, `summary`, `updatedAt`).
- `dictionary/entries/{id}.json`: full `DictionaryEntry` with `terms`, `definition`, `threadURL`, `statusURL`, optional `statusMessageID`, `updatedAt`, `references`, and optional `referencedBy` (codes of archive posts).

## Access flow (read-only)
1) Fetch `/config.json` to list `archiveChannels`.
2) For each channel, fetch `{channel.path}/data.json` to get `entries`.
3) For a specific post, fetch `{channel.path}/{entry.path}/data.json`; optionally fetch `comments.json`.
4) Resolve any `Image.path`/`Attachment.path` via the raw URL pattern.
5) Dictionary content is fetched separately via `dictionary/config.json` and `dictionary/entries/{id}.json`.

All responses are JSON; no authentication is required when using the raw URLs unless the repository is private.

## Typescript types:

```ts
export type Snowflake = string;

export enum AuthorType {
    DiscordInGuild = "discord-in-guild",
    DiscordLeftGuild = "discord-left-guild",
    DiscordExternal = "discord-external",
    DiscordDeleted = "discord-deleted",
    Unknown = "unknown",
}

export type BaseAuthor = {
    type: AuthorType,
    username: string, // Username

    reason?: string, // Optional reason for the author
    dontDisplay?: boolean // If true, this author will not be displayed in the by line
    url?: string, // URL to the author's profile or relevant page
}

export type DiscordWithNameAuthor = BaseAuthor & {
    type: AuthorType.DiscordInGuild | AuthorType.DiscordLeftGuild,
    id: Snowflake, // Discord user ID
    displayName: string, // Display name if different from username
    iconURL: string, // URL to the user's avatar
}

export type DiscordExternalAuthor = BaseAuthor & {
    type: AuthorType.DiscordExternal,
    id: Snowflake, // Discord user ID
    iconURL: string, // URL to the user's avatar
}

export type DiscordDeletedAuthor = BaseAuthor & {
    type: AuthorType.DiscordDeleted,
    id: Snowflake, // Discord user ID
}

export type UnknownAuthor = BaseAuthor & {
    type: AuthorType.Unknown,
}

export type AllAuthorPropertiesAccessor = BaseAuthor & {
    id?: Snowflake, // Discord user ID
    username: string, // Username
    displayName?: string, // Display name if different from username
    iconURL?: string, // URL to the user's avatar
}

export type DiscordAuthor = DiscordWithNameAuthor | DiscordExternalAuthor | DiscordDeletedAuthor;
export type Author = DiscordAuthor | UnknownAuthor;

export type Tag = { id: string; name: string }

export type Image = {
  id: Snowflake,
  name: string,
  url: string,
  description: string,
  contentType: string,
  width?: number,
  height?: number,
  canDownload: boolean,
  path?: string,
}

export type Attachment = {
  id: Snowflake,
  name: string,
  url: string,
  description: string,
  contentType: string,
  litematic?: { version?: string, size?: string, error?: string },
  wdl?: { version?: string, error?: string },
  youtube?: {
    title: string,
    author_name: string,
    author_url: string,
    thumbnail_url: string,
    thumbnail_width: number,
    thumbnail_height: number,
    width: number,
    height: number,
  },
  canDownload: boolean,
  path?: string,
}

export type NestedListItem = { title: string; isOrdered: boolean; items: (string | NestedListItem)[] };
export type SubmissionRecord = string | (string | NestedListItem)[];
export type SubmissionRecords = Record<string, SubmissionRecord>;

export type DiscordPostReference = {
  forumId?: Snowflake;
  threadId: Snowflake;
  continuingMessageIds?: Snowflake[];
  threadURL?: string;
  attachmentMessageId?: Snowflake;
  uploadMessageId?: Snowflake;
}

export type StyleInfo = {
  depth?: number;
  headerText?: string;
  isOrdered?: boolean;
}


export enum ReferenceType {
    DISCORD_LINK = "discordLink",
    DICTIONARY_TERM = "dictionaryTerm",
    ARCHIVED_POST = "archivedPost",
    USER_MENTION = "userMention",
    CHANNEL_MENTION = "channelMention",
}

export type ReferenceBase = {
    type: ReferenceType,
    matches: string[]
}

export type DiscordLinkReference = ReferenceBase & {
    type: ReferenceType.DISCORD_LINK,
    url: string,
    server: Snowflake,
    serverName?: string,
    serverJoinURL?: string,
    channel: Snowflake,
    message?: Snowflake,
}

export type DictionaryTermReference = ReferenceBase & {
    type: ReferenceType.DICTIONARY_TERM,
    term: string,
    id: Snowflake,
    url: string,
}

export type ArchivedPostReference = ReferenceBase & {
    type: ReferenceType.ARCHIVED_POST,
    id: Snowflake,
    code: string,
    url: string,
}

export type UserMentionReference = ReferenceBase & {
    type: ReferenceType.USER_MENTION,
    user: DiscordAuthor,
}

export type ChannelMentionReference = ReferenceBase & {
    type: ReferenceType.CHANNEL_MENTION,
    channelID: Snowflake,
    channelName?: string,
    channelURL?: string,
}

export type Reference = DiscordLinkReference | DictionaryTermReference | ArchivedPostReference | UserMentionReference | ChannelMentionReference;


export type ArchiveEntryData = {
  id: Snowflake;
  name: string;
  code: string;
  authors: Author[];
  endorsers: Author[];
  tags: Tag[];
  images: Image[];
  attachments: Attachment[];
  records: SubmissionRecords;
  styles: Record<string, StyleInfo>;
  references: Reference[];
  author_references: Reference[];
  post?: DiscordPostReference;
  timestamp?: number; // legacy
  archivedAt: number;
  updatedAt: number;
}

export interface ChannelRef {
  id: Snowflake;
  name: string;      // slug
  code: string;      // short code, like FL
  category: string;
  path: string;      // folder from repo root
  description: string;
  availableTags: string[]; // list of tag names available in this channel
}

export interface ChannelData extends Omit<ChannelRef, "path"> {
  currentCodeId: number;
  entries: EntryRef[];
}

export interface EntryRef {
  id: Snowflake;
  name: string;
  code: string;
  timestamp?: number; // legacy
  archivedAt: number;
  updatedAt: number;
  path: string; // folder within channel
  tags: string[]; // tag names available at the entry reference level
}

export interface ArchiveConfig {
  archiveChannels: ChannelRef[]
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  postSchema: any; // JSON Schema for validating posts
  postStyle: Record<string, StyleInfo>;
}

export type ArchiveComment = {
  id: string; // Unique identifier for the comment
  sender: Author;
  content: string; // The content of the comment
  attachments: Attachment[]; // List of attachments associated with the comment
  timestamp: number; // Timestamp of when the comment was made
}

export type DictionaryIndexEntry = {
  id: Snowflake,
  terms: string[]
  summary: string,
  updatedAt: number,
}

export type DictionaryConfig = {
  entries: DictionaryIndexEntry[]
}

export type DictionaryEntry = {
    id: Snowflake;
    terms: string[];
    definition: string;
    threadURL: string;
    statusURL: string;
    statusMessageID?: Snowflake;
    updatedAt: number;
    references: Reference[];
    referencedBy?: string[]; // list of codes of entries that reference this one
}
```

## Rendering records
The `styles` field in `ArchiveEntryData` can be used to override how specific records are rendered. Each key corresponds to a key in the `records` field, and the value is a `StyleInfo` object that can specify. Specific configs in the `styles` field in posts overrides the global `postStyle` from the root `config.json`, if present. Lists can be nested using the `NestedListItem` structure for complex formatting.

```ts
export function getEffectiveStyle(key: string, schemaStyles?: Record<string, StyleInfo>, recordStyles?: Record<string, StyleInfo>): StrictStyleInfo {
  const recordStyle = Object.hasOwn(recordStyles || {}, key) ? recordStyles![key] : null
  const schemaStyle = Object.hasOwn(schemaStyles || {}, key) ? schemaStyles![key] : null

  const style = {
    depth: 2,
    headerText: capitalizeFirstLetter(key),
    isOrdered: false,
  }
  if (schemaStyle) {
    if (schemaStyle.depth !== undefined) style.depth = schemaStyle.depth
    if (schemaStyle.headerText !== undefined) style.headerText = schemaStyle.headerText
    if (schemaStyle.isOrdered !== undefined) style.isOrdered = schemaStyle.isOrdered
  }
  if (recordStyle) {
    if (recordStyle.depth !== undefined) style.depth = recordStyle.depth
    if (recordStyle.headerText !== undefined) style.headerText = recordStyle.headerText
    if (recordStyle.isOrdered !== undefined) style.isOrdered = recordStyle.isOrdered
  }
  return style
}

export function nestedListToMarkdown(nestedList: NestedListItem, indentLevel: number = 0): string {
  const markdown: string[] = []
  const indent = "  ".repeat(indentLevel)
  if (nestedList.isOrdered) {
    nestedList.items.forEach((item, index) => {
      if (typeof item === "string") {
        markdown.push(`${indent}${index + 1}. ${item}`)
      } else if (typeof item === "object") {
        markdown.push(`${indent}${index + 1}. ${item.title}`)
        if (item.items.length > 0) {
          markdown.push(nestedListToMarkdown(item, indentLevel + 2))
        }
      }
    })
  } else {
    nestedList.items.forEach((item) => {
      if (typeof item === "string") {
        markdown.push(`${indent}- ${item}`)
      } else if (typeof item === "object") {
        markdown.push(`${indent}- ${item.title}`)
        if (item.items.length > 0) {
          markdown.push(nestedListToMarkdown(item, indentLevel + 1))
        }
      }
    })
  }
  return markdown.join("\n")
}

export function submissionRecordToMarkdown(value: SubmissionRecord, style?: StyleInfo): string {
  let markdown = ""
  if (Array.isArray(value)) {
    if (value.length !== 0) {
      markdown += value.map((item, i) => {
        if (typeof item === "string") {
          return style?.isOrdered ? `${i + 1}. ${item}` : `- ${item}`
        } else if (typeof item === "object") {
          return style?.isOrdered ? `${i + 1}. ${item.title}\n${nestedListToMarkdown(item, 2)}` : `- ${item.title}\n${nestedListToMarkdown(item, 1)}`
        }
        return ""
      }).join("\n")
    }
  } else {
    markdown += `${value}\n`
  }

  return markdown.trim()
}

export function postToMarkdown(record: SubmissionRecords, recordStyles?: Record<string, StyleInfo>, schemaStyles?: Record<string, StyleInfo>): string {
  let markdown = ""

  let isFirst = true

  const parentsRecorded = new Set<string>()
  for (const key in record) {
    const keyParts = key.split(":")
    for (let i = keyParts.length - 1; i > 0; i--) {
      const parentKey = keyParts.slice(0, i).join(":")
      if (!parentsRecorded.has(parentKey)) {
        const parentStyle = getEffectiveStyle(parentKey, schemaStyles, recordStyles)
        markdown += `\n${"#".repeat(parentStyle.depth)} ${parentStyle.headerText}\n`
        parentsRecorded.add(parentKey)
      } else {
        break
      }
    }

    parentsRecorded.add(key)

    const recordValue = record[key]
    const styles = getEffectiveStyle(key, schemaStyles, recordStyles)

    const text = submissionRecordToMarkdown(recordValue, styles)
    if (text.length > 0) {
      if (key !== "description" || !isFirst) {
        markdown += `\n${"#".repeat(styles.depth)} ${styles.headerText}\n`
      }
      isFirst = false
    }
    markdown += text
  }

  return markdown.trim()
}
```