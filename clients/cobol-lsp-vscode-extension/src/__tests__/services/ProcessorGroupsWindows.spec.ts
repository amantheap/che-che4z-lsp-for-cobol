/*
 * Copyright (c) 2023 Broadcom.
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

import { loadProcessorGroupCopybookPathsConfig } from "../../services/ProcessorGroups";

const WORKSPACE_URI = "file:///my/workspace";

jest.mock("fs", () => ({
  existsSync: jest.fn().mockReturnValue(true),
  readFileSync: jest.fn().mockImplementation((f) => {
    if (f == "procCfgPath") {
      return `{
                "pgroups": [
                    { 
                        "name": "DAF",
                        "copybook-extensions": [".copy"],
                        "copybook-file-encoding": "UTF-8",
                        "preprocessor": [
                            "IDMS",
                            { 
                                "name": "DaCo", 
                                "libs": ["/daco"]
                            }
                        ], 
                        "libs": ["/copy"]
                    },
                    {
                        "name": "IDMSPG",
                        "preprocessor": [ "IDMS" ]
                    }, 
                    {
                        "name": "ABS",
                        "libs": ["/abs"]
                    }
                ]
            }`;
    }
    if (f == "pgmCfgPath") {
      return `{
                "pgms": [ 
                    { "program": "/my/workspace/abs/TEST.cob", "pgroup": "ABS" },
                    { "program": "TEST.cob", "pgroup": "DAF" }, 
                    { "program": "*DAF.cob", "pgroup": "DAF" },
                    { "program": "IDMS/TEST.cob", "pgroup": "IDMSPG" }
                ]
            }`;
    }
    return undefined;
  }),
}));

jest.mock("vscode", () => ({
  Uri: {
    parse: jest.fn().mockImplementation((str: string) => {
      return {
        fsPath: str.replace("/c%3A", "c:").substring("file://".length),
      };
    }),
  },
  workspace: {
    getWorkspaceFolder: jest
      .fn()
      .mockReturnValue({ uri: { fsPath: "C:\\my\\workspace" } }),
    workspaceFolders: [{ uri: { fsPath: "C:\\my\\workspace" } }],
  },
}));

jest.mock("path", () => ({
  join: jest.fn().mockImplementation((...strs: string[]) => {
    if (strs[1] === "pgm_conf.json") {
      return "pgmCfgPath";
    }
    if (strs[1] === "proc_grps.json") {
      return "procCfgPath";
    }
    return strs.join("/");
  }),
  relative: jest.fn().mockImplementation((...strs: string[]) => {
    return strs[1].substring(strs[0].length + 1);
  }),
  isAbsolute: jest.fn().mockImplementation((...strs: string[]) => {
    return strs[0].startsWith("\\");
  }),
  sep: "\\",
}));

it.only("Processor groups configuration provides lib path in Windows", () => {
  const item = {
    scopeUri: "file:///c%3A/my/workspace" + "/TEST.cob",
    section: "cobol-lsp.cpy-manager.paths-local",
  };

  const result = loadProcessorGroupCopybookPathsConfig(item, []);
  expect(result).toStrictEqual(["/copy"]);
});
