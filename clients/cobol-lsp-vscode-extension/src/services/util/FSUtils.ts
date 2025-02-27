/*
 * Copyright (c) 2020 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Broadcom, Inc. - initial API and implementation
 */

import { existsSync, readdirSync } from "fs";
import * as fs from "fs";
import * as path from "path";
import * as glob from "glob";
import * as urlUtil from "url";
import { SettingsUtils } from "./SettingsUtils";
import { Uri } from "vscode";

/**
 * This method is responsible to return a valid URI without extension if the extension is not provided or an URI
 * that contains an allowed extension.
 * @param folder is the first part of the URI referred to the folder defined in the setting.json
 * @param entityName is the name of entity identified by the LSP server that needs to be found locally
 * @param extensions an optional parameter to produce an URI of an allowed extension list, verifying that
 * this URI really exists on FS.
 *
 */
export function getURIFrom(
  folder: string,
  entityName: string,
  extensions?: string[],
): urlUtil.URL {
  if (!extensions) {
    const url = new urlUtil.URL(path.join(folder, entityName));
    if (existsSync(url)) {
      return url;
    }
  } else {
    const fileList = readdirSync(new urlUtil.URL(folder));
    for (const extension of extensions) {
      const copybookFileWithExtension = (entityName + extension).toUpperCase();
      const found = fileList.find(
        (filename) =>
          filename.toUpperCase() === copybookFileWithExtension.toUpperCase(),
      );
      if (found) {
        return new urlUtil.URL(path.join(folder, found));
      }
    }
  }
}

/**
 * This function construct an URI from a valid resource provided from the setting configuration
 * @param resource represent the file to search within the workspace folder list
 * @return an URI representation of the file or undefined if not found
 */
export function getURIFromResource(resource: string): urlUtil.URL[] {
  const uris: urlUtil.URL[] = [];
  for (const workspaceFolderPath of SettingsUtils.getWorkspaceFoldersPath()) {
    const workspaceFolder = workspaceFolderPath.replace(/\/(.*:)/, "$1");
    const uri = isAbsolute(resource)
      ? urlUtil.pathToFileURL(resource)
      : new urlUtil.URL(
          path.normalize(path.join("file://" + workspaceFolder, resource)),
        );

    if (fs.existsSync(uri)) {
      uris.push(uri);
    }
  }
  return uris;
}

/**
 * This method scans the list of folders as given input and find the required entity name within the folder.
 * If found returns its URI representation
 * @param copybookName name of the entity asked by the server
 * @param copybookFolders list of folders from where to search the copybook
 * @param extensions list of possible copybooks extensions
 */
export function searchCopybookInWorkspace(
  copybookName: string,
  copybookFolders: string[],
  extensions: string[],
): string | undefined {
  for (const workspaceFolderPath of SettingsUtils.getWorkspaceFoldersPath()) {
    const workspaceFolder = workspaceFolderPath.replace(/\/(.*:)/, "$1");
    for (const p of copybookFolders) {
      for (const ext of extensions) {
        const searchResult = globSearch(workspaceFolder, p, copybookName, ext);
        if (searchResult) {
          const root = path.parse(searchResult).root;
          const urlPath = searchResult
            .substring(root.length)
            .split(path.sep)
            .map((s) => encodeURIComponent(s))
            .join(path.sep);
          return new urlUtil.URL("file://" + root + urlPath).href;
        }
      }
    }
  }
  return undefined;
}

const backwardSlashRegex = new RegExp("\\\\", "g");
function globSearch(
  workspaceFolder: string,
  resource: string,
  copybookName: string,
  ext: string,
): string | undefined {
  const pathName: string = isAbsolute(resource)
    ? resource
    : path.normalize(path.join(workspaceFolder, resource));
  const segments = pathName.split(path.sep);
  const cwdSegments: string[] = [];
  for (const s of segments) {
    if (!glob.hasMagic(s)) {
      cwdSegments.push(s);
    } else {
      break;
    }
  }
  // One must use forward-slashes only in glob expressions
  const cwd = path
    .resolve("/", ...cwdSegments)
    .replace(backwardSlashRegex, "/");
  const normalizePathName = pathName.replace(backwardSlashRegex, "/");
  let pattern =
    normalizePathName === cwd
      ? ""
      : normalizePathName.replace(cwd.endsWith("/") ? cwd : cwd + "/", "");
  const suffix =
    (pattern.length == 0 || pattern.endsWith("/") ? "" : "/") +
    copybookName +
    ext;
  pattern = pattern + suffix;
  const result = glob.sync(pattern, { cwd, dot: true });
  // TODO report the case with more then one copybook fit the pattern.
  return result[0] ? path.join(cwd, result[0]) : undefined;
}

export function getProgramNameFromUri(uri: string): string {
  const fullPath = Uri.parse(uri).fsPath;
  return path.basename(fullPath, path.extname(fullPath));
}

function isAbsolute(resource: string): boolean {
  return path.resolve(resource) === path.normalize(resource);
}

/**
 * This method delete the folder's content.
 * @param pathToClear represents the folder to be cleaned.
 */
export function cleanDirectory(pathToClear: string) {
  if (fs.existsSync(pathToClear)) {
    readdirSync(pathToClear).forEach((f) =>
      fs.rmSync(path.join(pathToClear, `${f}`), { recursive: true }),
    );
  }
}
