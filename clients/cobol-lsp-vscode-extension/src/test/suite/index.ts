/*
 * Copyright (c) 2022 Broadcom.
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

import * as path from "path";
import * as Mocha from "mocha";
import * as glob from "glob";
import * as process from "process";

export async function run(): Promise<void> {
  const sourceRoot = path.join(__dirname, "..", "..");

  // initialize nyc code coverage
  const NYC = require("nyc");
  const nyc = new NYC({
    cwd: path.join(sourceRoot, ".."),
    reporter: ["lcov"],
    hookRequire: true,
    exclude: ["**/test/**", ".vscode-test/**"],
  });

  const is_vscode = process.execPath.includes("Code");
  // only on VSCode
  if (is_vscode) {
    // decache files on windows to be hookable by nyc
    let decache = require("decache");
    glob
      .sync("**/**.js", {
        cwd: sourceRoot,
      })
      .forEach((file) => {
        decache(path.join(sourceRoot, file));
      });

    nyc.createTempDirectory();
    nyc.wrap();
  }

  // Create the mocha test
  const mocha = new Mocha({ ui: "tdd", color: true });
  const testsPath = path.join(__dirname, "..");

  const files = await new Promise<string[]>((resolve, reject) => {
    glob(
      is_vscode ? "**/**.test.js" : "**/integration.test.js",
      { cwd: testsPath },
      (err, files) => {
        if (err) reject(err);
        else resolve(files);
      },
    );
  });

  // Add files to the test suite
  files.forEach((file) => mocha.addFile(path.resolve(testsPath, file)));

  await new Promise((resolve, reject) => {
    // Run the mocha test
    mocha.run((failures) => {
      if (failures > 0) {
        reject(new Error(`${failures} tests failed.`));
      } else {
        resolve(undefined);
      }
    });
  });

  if (is_vscode) {
    // report code coverage
    nyc.writeCoverageFile();
    await nyc.report();
    console.log("Report created");
  }
}
